package com.example.teamflow.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectFile;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RiskSnapshot;
import com.example.teamflow.entity.SectionComment;
import com.example.teamflow.entity.SectionContent;
import com.example.teamflow.entity.SectionReview;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.ProjectFileMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.RiskSnapshotMapper;
import com.example.teamflow.mapper.SectionCommentMapper;
import com.example.teamflow.mapper.SectionContentMapper;
import com.example.teamflow.mapper.SectionReviewMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public class SectionAgentContextBuilder {
    public static final int MAX_CONTEXT_LENGTH = 16000;
    private static final int MAX_ITEMS_PER_CATEGORY = 20;
    private static final int MAX_TEXT_LENGTH = 1200;
    private static final int MAX_SECTION_BODY_LENGTH = 2000;

    private static final List<String> CATEGORY_ORDER = List.of(
            "PROJECT_OVERVIEW",
            "CURRENT_SECTION",
            "PREVIOUS_SECTIONS",
            "MEMBERS",
            "TASKS",
            "RISKS",
            "FILES",
            "COMMENTS",
            "REVIEW_HISTORY"
    );
    private static final Comparator<ProjectSection> SECTION_ORDER = Comparator
            .comparing(ProjectSection::getSortOrder, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ProjectSection::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ProjectSectionMapper projectSectionMapper;
    private final SectionContentMapper sectionContentMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectRoleMapper projectRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final TaskMapper taskMapper;
    private final RiskSnapshotMapper riskSnapshotMapper;
    private final ProjectFileMapper projectFileMapper;
    private final SectionCommentMapper sectionCommentMapper;
    private final SectionReviewMapper sectionReviewMapper;

    public SectionAgentContextBuilder(ProjectSectionMapper projectSectionMapper,
                                      SectionContentMapper sectionContentMapper,
                                      ProjectMemberMapper projectMemberMapper,
                                      ProjectRoleMapper projectRoleMapper,
                                      SysUserMapper sysUserMapper,
                                      TaskMapper taskMapper,
                                      RiskSnapshotMapper riskSnapshotMapper,
                                      ProjectFileMapper projectFileMapper,
                                      SectionCommentMapper sectionCommentMapper,
                                      SectionReviewMapper sectionReviewMapper) {
        this.projectSectionMapper = projectSectionMapper;
        this.sectionContentMapper = sectionContentMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectRoleMapper = projectRoleMapper;
        this.sysUserMapper = sysUserMapper;
        this.taskMapper = taskMapper;
        this.riskSnapshotMapper = riskSnapshotMapper;
        this.projectFileMapper = projectFileMapper;
        this.sectionCommentMapper = sectionCommentMapper;
        this.sectionReviewMapper = sectionReviewMapper;
    }

    public String build(Project project, ProjectSection section, EffectiveAgentProfile profile) {
        if (profile == null || profile.getContextScope() == null || profile.getContextScope().isEmpty()) {
            return "";
        }

        Set<String> selected = new HashSet<>();
        for (String category : profile.getContextScope()) {
            if (category != null) {
                selected.add(category);
            }
        }

        Long projectId = project != null && project.getId() != null
                ? project.getId()
                : section == null ? null : section.getProjectId();
        StringBuilder context = new StringBuilder();
        for (String category : CATEGORY_ORDER) {
            if (!selected.contains(category)) {
                continue;
            }
            switch (category) {
                case "PROJECT_OVERVIEW" -> appendProjectOverview(context, project);
                case "CURRENT_SECTION" -> appendCurrentSection(context, section);
                case "PREVIOUS_SECTIONS" -> appendPreviousSections(context, projectId, section);
                case "MEMBERS" -> appendMembers(context, projectId);
                case "TASKS" -> appendTasks(context, projectId);
                case "RISKS" -> appendRisk(context, projectId);
                case "FILES" -> appendFiles(context, projectId);
                case "COMMENTS" -> appendComments(context, section == null ? null : section.getId());
                case "REVIEW_HISTORY" -> appendReviewHistory(context, section == null ? null : section.getId());
                default -> {
                }
            }
        }
        return truncate(context.toString(), MAX_CONTEXT_LENGTH);
    }

    private void appendProjectOverview(StringBuilder context, Project project) {
        appendHeading(context, "【项目概述】");
        if (project == null) {
            appendEmpty(context);
            return;
        }
        appendField(context, "项目名称", project.getProjectName());
        appendField(context, "描述", project.getDescription());
        appendField(context, "状态", project.getStatus());
        appendField(context, "开始日期", project.getStartDate());
        appendField(context, "结束日期", project.getEndDate());
    }

    private void appendCurrentSection(StringBuilder context, ProjectSection section) {
        appendHeading(context, "【当前章节】");
        if (section == null) {
            appendEmpty(context);
            return;
        }
        appendField(context, "章节编码", section.getSectionCode());
        appendField(context, "章节名称", section.getSectionName());
        appendField(context, "描述", section.getDescription());
        appendField(context, "状态", section.getStatus());
    }

    private void appendPreviousSections(StringBuilder context, Long projectId, ProjectSection currentSection) {
        appendHeading(context, "【前置章节】");
        if (projectId == null || currentSection == null || currentSection.getId() == null) {
            appendEmpty(context);
            return;
        }

        LambdaQueryWrapper<ProjectSection> previousSectionQuery = new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, projectId);
        if (currentSection.getSortOrder() == null) {
            previousSectionQuery
                    .isNull(ProjectSection::getSortOrder)
                    .lt(ProjectSection::getId, currentSection.getId());
        } else {
            previousSectionQuery.and(earlier -> earlier
                    .isNull(ProjectSection::getSortOrder)
                    .or()
                    .lt(ProjectSection::getSortOrder, currentSection.getSortOrder())
                    .or(sameOrder -> sameOrder
                            .eq(ProjectSection::getSortOrder, currentSection.getSortOrder())
                            .lt(ProjectSection::getId, currentSection.getId())));
        }
        previousSectionQuery
                .orderByAsc(ProjectSection::getSortOrder)
                .orderByAsc(ProjectSection::getId)
                .last("LIMIT " + MAX_ITEMS_PER_CATEGORY);

        List<ProjectSection> previousSections = safeList(projectSectionMapper.selectList(
                        previousSectionQuery
                )).stream()
                .filter(section -> section != null && SECTION_ORDER.compare(section, currentSection) < 0)
                .sorted(SECTION_ORDER)
                .limit(MAX_ITEMS_PER_CATEGORY)
                .toList();
        if (previousSections.isEmpty()) {
            appendEmpty(context);
            return;
        }

        for (ProjectSection previousSection : previousSections) {
            context.append("- ");
            appendInlineField(context, "编码", previousSection.getSectionCode());
            appendInlineField(context, "名称", previousSection.getSectionName());
            appendInlineField(context, "描述", previousSection.getDescription());
            appendInlineField(context, "状态", previousSection.getStatus());
            context.append('\n');
            SectionContent latest = previousSection.getId() == null ? null : safeList(sectionContentMapper.selectList(
                            new LambdaQueryWrapper<SectionContent>()
                                    .eq(SectionContent::getProjectId, projectId)
                                    .eq(SectionContent::getSectionId, previousSection.getId())
                                    .orderByDesc(SectionContent::getVersionNo)
                                    .orderByDesc(SectionContent::getId)
                                    .last("LIMIT 1")
                    )).stream()
                    .filter(content -> content != null)
                    .findFirst()
                    .orElse(null);
            if (latest == null) {
                context.append("  内容：暂无数据\n");
                continue;
            }
            context.append("  ");
            appendInlineField(context, "版本", latest.getVersionNo());
            appendInlineField(context, "标题", latest.getTitle());
            context.append('\n');
            context.append("  正文：")
                    .append(truncate(value(latest.getBody()), MAX_SECTION_BODY_LENGTH))
                    .append('\n');
        }
    }

    private void appendMembers(StringBuilder context, Long projectId) {
        appendHeading(context, "【成员】");
        if (projectId == null) {
            appendEmpty(context);
            return;
        }
        List<ProjectMember> members = safeList(projectMemberMapper.selectList(
                        new LambdaQueryWrapper<ProjectMember>()
                                .eq(ProjectMember::getProjectId, projectId)
                                .orderByAsc(ProjectMember::getId)
                                .last("LIMIT " + MAX_ITEMS_PER_CATEGORY)
                )).stream()
                .filter(member -> member != null)
                .limit(MAX_ITEMS_PER_CATEGORY)
                .toList();
        if (members.isEmpty()) {
            appendEmpty(context);
            return;
        }

        Set<Long> roleIds = new LinkedHashSet<>();
        Set<Long> userIds = new LinkedHashSet<>();
        for (ProjectMember member : members) {
            if (member.getProjectRoleId() != null) {
                roleIds.add(member.getProjectRoleId());
            }
            if (member.getUserId() != null) {
                userIds.add(member.getUserId());
            }
        }
        Map<Long, ProjectRole> roles = indexById(
                roleIds.isEmpty() ? List.of() : safeList(projectRoleMapper.selectBatchIds(roleIds)),
                ProjectRole::getId
        );
        Map<Long, SysUser> users = indexById(
                userIds.isEmpty() ? List.of() : safeList(sysUserMapper.selectBatchIds(userIds)),
                SysUser::getId
        );

        for (ProjectMember member : members) {
            ProjectRole role = roles.get(member.getProjectRoleId());
            SysUser user = users.get(member.getUserId());
            String userName = user == null ? "" : preferredUserName(user);
            context.append("- ");
            appendInlineField(context, "成员角色", member.getMemberRole());
            appendInlineField(context, "项目角色", role == null ? "" : role.getRoleName());
            appendInlineField(context, "姓名", userName);
            context.append('\n');
        }
    }

    private void appendTasks(StringBuilder context, Long projectId) {
        appendHeading(context, "【任务】");
        if (projectId == null) {
            appendEmpty(context);
            return;
        }
        List<Task> tasks = safeList(taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .orderByDesc(Task::getUpdateTime)
                        .orderByDesc(Task::getId)
                        .last("LIMIT " + MAX_ITEMS_PER_CATEGORY)
        ));
        appendItemsOrEmpty(context, tasks, task -> {
            context.append("- ");
            appendInlineField(context, "标题", task.getTitle());
            appendInlineField(context, "状态", task.getStatus());
            appendInlineField(context, "优先级", task.getPriority());
            appendInlineField(context, "截止日期", task.getDueDate());
            appendInlineField(context, "阻塞原因", task.getBlockReason());
            context.append('\n');
        });
    }

    private void appendRisk(StringBuilder context, Long projectId) {
        appendHeading(context, "【风险】");
        if (projectId == null) {
            appendEmpty(context);
            return;
        }
        RiskSnapshot latest = safeList(riskSnapshotMapper.selectList(
                        new LambdaQueryWrapper<RiskSnapshot>()
                                .eq(RiskSnapshot::getProjectId, projectId)
                                .orderByDesc(RiskSnapshot::getSnapshotTime)
                                .orderByDesc(RiskSnapshot::getId)
                                .last("LIMIT 1")
                )).stream()
                .filter(snapshot -> snapshot != null)
                .findFirst()
                .orElse(null);
        if (latest == null) {
            appendEmpty(context);
            return;
        }
        context.append("- ");
        appendInlineField(context, "风险等级", latest.getRiskLevel());
        appendInlineField(context, "分数", latest.getRiskScore());
        appendInlineField(context, "趋势", latest.getTrend());
        appendInlineField(context, "摘要", latest.getSummary());
        context.append('\n');
    }

    private void appendFiles(StringBuilder context, Long projectId) {
        appendHeading(context, "【文件】");
        if (projectId == null) {
            appendEmpty(context);
            return;
        }
        List<ProjectFile> files = safeList(projectFileMapper.selectList(
                new LambdaQueryWrapper<ProjectFile>()
                        .eq(ProjectFile::getProjectId, projectId)
                        .orderByDesc(ProjectFile::getUpdateTime)
                        .orderByDesc(ProjectFile::getId)
                        .last("LIMIT " + MAX_ITEMS_PER_CATEGORY)
        ));
        appendItemsOrEmpty(context, files, file -> {
            context.append("- ");
            appendInlineField(context, "文件名", file.getFileName());
            appendInlineField(context, "文件类型", file.getFileType());
            appendInlineField(context, "文档类型", file.getDocumentType());
            appendInlineField(context, "描述", file.getDescription());
            context.append('\n');
        });
    }

    private void appendComments(StringBuilder context, Long sectionId) {
        appendHeading(context, "【评论】");
        if (sectionId == null) {
            appendEmpty(context);
            return;
        }
        List<SectionComment> comments = safeList(sectionCommentMapper.selectList(
                new LambdaQueryWrapper<SectionComment>()
                        .eq(SectionComment::getSectionId, sectionId)
                        .orderByDesc(SectionComment::getCreateTime)
                        .orderByDesc(SectionComment::getId)
                        .last("LIMIT " + MAX_ITEMS_PER_CATEGORY)
        ));
        appendItemsOrEmpty(context, comments, comment -> {
            context.append("- ");
            appendInlineField(context, "评论", comment.getCommentText());
            appendInlineField(context, "解决状态", resolvedState(comment.getResolvedFlag()));
            context.append('\n');
        });
    }

    private void appendReviewHistory(StringBuilder context, Long sectionId) {
        appendHeading(context, "【审核历史】");
        if (sectionId == null) {
            appendEmpty(context);
            return;
        }
        List<SectionReview> reviews = safeList(sectionReviewMapper.selectList(
                new LambdaQueryWrapper<SectionReview>()
                        .eq(SectionReview::getSectionId, sectionId)
                        .orderByDesc(SectionReview::getCreateTime)
                        .orderByDesc(SectionReview::getId)
                        .last("LIMIT " + MAX_ITEMS_PER_CATEGORY)
        ));
        appendItemsOrEmpty(context, reviews, review -> {
            context.append("- ");
            appendInlineField(context, "审核结果", review.getReviewResult());
            appendInlineField(context, "审核意见", review.getReviewComment());
            context.append('\n');
        });
    }

    private static <T> void appendItemsOrEmpty(StringBuilder context, List<T> items,
                                                java.util.function.Consumer<T> renderer) {
        int rendered = 0;
        for (T item : items) {
            if (item == null) {
                continue;
            }
            renderer.accept(item);
            rendered++;
            if (rendered == MAX_ITEMS_PER_CATEGORY) {
                break;
            }
        }
        if (rendered == 0) {
            appendEmpty(context);
        }
    }

    private static void appendHeading(StringBuilder context, String heading) {
        if (!context.isEmpty()) {
            context.append('\n');
        }
        context.append(heading).append('\n');
    }

    private static void appendField(StringBuilder context, String label, Object scalar) {
        context.append(label).append('：').append(truncate(value(scalar), MAX_TEXT_LENGTH)).append('\n');
    }

    private static void appendInlineField(StringBuilder context, String label, Object scalar) {
        context.append(label).append('：').append(truncate(value(scalar), MAX_TEXT_LENGTH)).append("；");
    }

    private static void appendEmpty(StringBuilder context) {
        context.append("暂无数据\n");
    }

    private static String resolvedState(Integer resolvedFlag) {
        if (resolvedFlag == null) {
            return "未知";
        }
        return resolvedFlag == 1 ? "已解决" : "未解决";
    }

    private static String preferredUserName(SysUser user) {
        String realName = value(user.getRealName());
        return realName.isBlank() ? value(user.getUsername()) : realName;
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String truncate(String value, int maxLength) {
        int end = Math.min(value.length(), maxLength);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        } else if (end < value.length() && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        if (end == value.length()) {
            return value;
        }
        return value.substring(0, end);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> Map<Long, T> indexById(Collection<T> values, Function<T, Long> idExtractor) {
        Map<Long, T> indexed = new HashMap<>();
        for (T value : values) {
            if (value != null) {
                Long id = idExtractor.apply(value);
                if (id != null) {
                    indexed.put(id, value);
                }
            }
        }
        return indexed;
    }
}
