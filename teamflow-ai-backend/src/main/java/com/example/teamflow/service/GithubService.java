package com.example.teamflow.service;

import com.example.teamflow.ai.AiClient;
import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.Project;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GithubService {

    @Value("${github.token:}")
    private String token;

    private final ProjectService projectService;
    private final SectionService sectionService;
    private final AiClient aiClient;

    private GitHub getGitHub() throws IOException {
        if (!StringUtils.hasText(token)) {
            throw new BizException(400, "GitHub 令牌未配置，请配置环境变量 GITHUB_TOKEN。");
        }
        return new GitHubBuilder().withOAuthToken(token).build();
    }

    private GHRepository getRepo(Long projectId) throws IOException {
        Project project = projectService.getProject(projectId);
        String repoName = project.getGithubRepo();
        if (!StringUtils.hasText(repoName)) {
            throw new BizException(400, "项目未绑定 GitHub 仓库，请先在项目设置中绑定。");
        }
        String cleanRepo = cleanRepoName(repoName);
        return getGitHub().getRepository(cleanRepo);
    }

    private String cleanRepoName(String repo) {
        if (repo == null) return "";
        String clean = repo.trim();
        if (clean.endsWith(".git")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        if (clean.startsWith("git@github.com:")) {
            clean = clean.substring("git@github.com:".length());
        }
        if (clean.startsWith("https://github.com/")) {
            clean = clean.substring("https://github.com/".length());
        }
        if (clean.startsWith("http://github.com/")) {
            clean = clean.substring("http://github.com/".length());
        }
        if (clean.startsWith("github.com/")) {
            clean = clean.substring("github.com/".length());
        }
        if (clean.startsWith("repos/")) {
            clean = clean.substring("repos/".length());
        }
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    public Map<String, Object> getBranchInfo(Long projectId, String sectionCode) {
        try {
            GHRepository repo = getRepo(projectId);
            String branchName = "feature/" + sectionCode.toLowerCase();
            GHRef ref = null;
            try {
                ref = repo.getRef("heads/" + branchName);
            } catch (IOException e) {
                // Ignore if ref does not exist
            }
            Map<String, Object> res = new HashMap<>();
            res.put("exists", ref != null);
            res.put("branchName", branchName);
            if (ref != null) {
                res.put("sha", ref.getObject().getSha());
                res.put("url", repo.getHtmlUrl() + "/tree/" + branchName);
            } else {
                res.put("defaultBranch", repo.getDefaultBranch());
            }
            return res;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "获取分支信息失败: " + e.getMessage());
        }
    }

    public void createBranch(Long projectId, String sectionCode) {
        try {
            GHRepository repo = getRepo(projectId);
            String defaultBranch = repo.getDefaultBranch();
            GHRef defaultRef = repo.getRef("heads/" + defaultBranch);
            String sha = defaultRef.getObject().getSha();
            String branchName = "feature/" + sectionCode.toLowerCase();

            try {
                repo.getRef("heads/" + branchName);
                throw new BizException(400, "分支 " + branchName + " 已存在");
            } catch (IOException e) {
                // branch doesn't exist, proceed to create
            }

            repo.createRef("refs/heads/" + branchName, sha);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "创建分支失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getCommits(Long projectId, String sectionCode) {
        try {
            GHRepository repo = getRepo(projectId);
            String branchName = "feature/" + sectionCode.toLowerCase();

            PagedIterable<GHCommit> commitList = null;
            try {
                try {
                    repo.getRef("heads/" + branchName);
                    commitList = repo.queryCommits().from(branchName).list();
                } catch (IOException e) {
                    String defaultBranch = repo.getDefaultBranch();
                    if (StringUtils.hasText(defaultBranch)) {
                        commitList = repo.queryCommits().from(defaultBranch).list();
                    }
                }
            } catch (Exception e) {
                // Return empty list gracefully if branch commits cannot be read (e.g. empty repository)
                return new ArrayList<>();
            }

            List<Map<String, Object>> list = new ArrayList<>();
            if (commitList != null) {
                int count = 0;
                for (GHCommit commit : commitList) {
                    if (count >= 10) break;
                    Map<String, Object> map = new HashMap<>();
                    map.put("sha", commit.getSHA1());
                    map.put("shortSha", commit.getSHA1().substring(0, 7));
                    map.put("message", commit.getCommitShortInfo().getMessage());
                    map.put("author", commit.getCommitShortInfo().getAuthor().getName());
                    map.put("date", commit.getCommitShortInfo().getAuthoredDate().toInstant().toString());
                    map.put("url", repo.getHtmlUrl() + "/commit/" + commit.getSHA1());
                    list.add(map);
                    count++;
                }
            }
            return list;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getPullRequest(Long projectId, String sectionCode) {
        try {
            GHRepository repo = getRepo(projectId);
            String headBranchName = "feature/" + sectionCode.toLowerCase();

            List<GHPullRequest> prs = repo.getPullRequests(GHIssueState.OPEN);
            GHPullRequest targetPr = null;
            for (GHPullRequest pr : prs) {
                if (pr.getHead().getRef().equals(headBranchName)) {
                    targetPr = pr;
                    break;
                }
            }

            Map<String, Object> res = new HashMap<>();
            res.put("exists", targetPr != null);
            if (targetPr != null) {
                res.put("number", targetPr.getNumber());
                res.put("title", targetPr.getTitle());
                res.put("body", targetPr.getBody());
                res.put("state", targetPr.getState().name());
                res.put("url", targetPr.getHtmlUrl());
                res.put("draft", targetPr.isDraft());
                res.put("merged", targetPr.isMerged());

                List<Map<String, Object>> files = new ArrayList<>();
                for (GHPullRequestFileDetail file : targetPr.listFiles()) {
                    Map<String, Object> fileMap = new HashMap<>();
                    fileMap.put("filename", file.getFilename());
                    fileMap.put("status", file.getStatus());
                    fileMap.put("additions", file.getAdditions());
                    fileMap.put("deletions", file.getDeletions());
                    fileMap.put("changes", file.getChanges());
                    files.add(fileMap);
                }
                res.put("files", files);
            }
            return res;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "获取 PR 信息失败: " + e.getMessage());
        }
    }

    public Map<String, Object> createPullRequest(Long projectId, String sectionCode, String title, String body) {
        try {
            GHRepository repo = getRepo(projectId);
            String headBranchName = "feature/" + sectionCode.toLowerCase();
            String baseBranchName = repo.getDefaultBranch();

            GHPullRequest pr = repo.createPullRequest(title, headBranchName, baseBranchName, body);

            // Update local TeamFlowAI section status to REVIEWING
            sectionService.updateSectionStatus(projectId, sectionCode, "REVIEWING");

            Map<String, Object> res = new HashMap<>();
            res.put("number", pr.getNumber());
            res.put("title", pr.getTitle());
            res.put("url", pr.getHtmlUrl());
            return res;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "创建 PR 失败: " + e.getMessage());
        }
    }

    public String triggerAiReview(Long projectId, String sectionCode) {
        try {
            GHRepository repo = getRepo(projectId);
            String headBranchName = "feature/" + sectionCode.toLowerCase();

            List<GHPullRequest> prs = repo.getPullRequests(GHIssueState.OPEN);
            GHPullRequest targetPr = null;
            for (GHPullRequest pr : prs) {
                if (pr.getHead().getRef().equals(headBranchName)) {
                    targetPr = pr;
                    break;
                }
            }

            if (targetPr == null) {
                throw new BizException(404, "找不到该章节关联的开启 PR，请先创建 PR。");
            }

            StringBuilder diffBuilder = new StringBuilder();
            int fileCount = 0;
            for (GHPullRequestFileDetail file : targetPr.listFiles()) {
                if (fileCount >= 10) {
                    diffBuilder.append("\n... (为了保护上下文长度，仅展示前 10 个文件的变更详情)\n");
                    break;
                }
                diffBuilder.append("文件: ").append(file.getFilename()).append("\n");
                diffBuilder.append("变更类型: ").append(file.getStatus()).append("\n");
                String patch = file.getPatch();
                if (StringUtils.hasText(patch)) {
                    if (patch.length() > 1500) {
                        patch = patch.substring(0, 1500) + "\n... (补丁过长已自动截断)\n";
                    }
                    diffBuilder.append("修改内容 (diff):\n").append(patch).append("\n");
                } else {
                    diffBuilder.append("(二进制文件或无文本改动)\n");
                }
                diffBuilder.append("--------------------------------------------------\n");
                fileCount++;
            }

            if (diffBuilder.length() == 0) {
                diffBuilder.append("(无文本改动详情)\n");
            }

            String prompt = """
                    你是一个卓越的资深软件架构师与代码评审专家。请对以下 GitHub Pull Request 提交的代码变更进行专业而深刻的代码审查（Code Review）。
                    
                    要求：
                    1. 指出代码中存在的潜在 Bugs、安全漏洞（如 SQL 注入、越权、并发问题等）、代码坏味道（Code Smells）、性能瓶颈或是不符合高并发架构的代码写法。
                    2. 提供具体、可落地的修改代码意见（建议使用 Markdown 代码块表示修改前和修改后的代码对比）。
                    3. 评价整体代码的设计模式与架构合理性。
                    4. 回答必须精炼、直观、切中要害，使用 Markdown 格式。
                    
                    代码变更详情 (Git Diff)：
                    %s
                    """.formatted(diffBuilder.toString());

            String reviewComment;
            if (aiClient.enabled()) {
                reviewComment = aiClient.chat(prompt);
            } else {
                reviewComment = """
                        ### 🤖 AI Code Review (本地模拟)
                        
                        已成功捕获当前开发分支的变更。由于系统未配置 Zhipu API Key，以下为开发准则审核建议：
                        
                        1. **架构层级合规性**：请确保所有 Controller 仅调用 Service 层，避免直接访问 Mapper。
                        2. **代码防错与规范**：
                           - 请确保涉及数据库多表更新的方法均加上了 `@Transactional(rollbackFor = Exception.class)`。
                           - 在外部参数接口上均配置必要的 `@Valid` 或校验参数。
                           - 使用 `LambdaQueryWrapper` 替代字符串拼接，防范 SQL 注入风险。
                        3. **持续集成**：请本地验证 `mvn clean test` 以确认没有引入 regression 问题。
                        """;
            }

            targetPr.comment(reviewComment);
            return reviewComment;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "触发 AI 自动评审失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getWorkflowRuns(Long projectId) {
        try {
            GHRepository repo = getRepo(projectId);
            PagedIterable<GHWorkflowRun> runs = repo.queryWorkflowRuns().list();
            List<Map<String, Object>> list = new ArrayList<>();
            int count = 0;
            for (GHWorkflowRun run : runs) {
                if (count >= 5) break;
                Map<String, Object> map = new HashMap<>();
                map.put("id", run.getId());
                map.put("name", run.getName());
                map.put("status", run.getStatus().name());
                map.put("conclusion", run.getConclusion() != null ? run.getConclusion().name() : "RUNNING");
                map.put("event", run.getEvent().name());
                map.put("htmlUrl", run.getHtmlUrl());
                map.put("createTime", run.getCreatedAt().toInstant().toString());
                list.add(map);
                count++;
            }
            return list;
        } catch (Exception e) {
            // Workflows not enabled or empty repository
            return new ArrayList<>();
        }
    }
}
