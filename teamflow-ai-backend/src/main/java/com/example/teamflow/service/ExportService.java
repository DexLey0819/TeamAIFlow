package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.ExportRequestDTO;
import com.example.teamflow.entity.AiRecord;
import com.example.teamflow.entity.ExportRecord;
import com.example.teamflow.entity.ProgressRecord;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.SectionContent;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.AiRecordMapper;
import com.example.teamflow.mapper.ExportRecordMapper;
import com.example.teamflow.mapper.ProgressRecordMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.SectionContentMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskMapper;
import com.example.teamflow.vo.CompletenessVO;
import com.example.teamflow.vo.ContributionVO;
import com.example.teamflow.vo.ExportRecordVO;
import com.example.teamflow.vo.RiskTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExportService {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ExportRecordMapper exportRecordMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final SectionContentMapper sectionContentMapper;
    private final TaskMapper taskMapper;
    private final ProgressRecordMapper progressRecordMapper;
    private final AiRecordMapper aiRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectService projectService;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final StatisticsService statisticsService;

    @Value("${file.upload-path}")
    private String uploadPath;

    public ExportRecordVO create(Long projectId, ExportRequestDTO request) {
        Project project = projectService.getProject(projectId);
        projectService.ensureManager(project);
        SysUser current = authService.currentUser();
        CompletenessVO readiness = statisticsService.completeness(project);

        LocalDateTime now = LocalDateTime.now();
        ExportRecord record = new ExportRecord();
        record.setProjectId(projectId);
        record.setCreatorId(current.getId());
        record.setExportScope(normalizeScope(request));
        record.setStatus("GENERATED");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        exportRecordMapper.insert(record);

        try {
            Path exportDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("exports");
            Files.createDirectories(exportDir);

            String fileName = stableFileName(project, record.getId(), now);
            Path filePath = exportDir.resolve(fileName);
            Files.writeString(filePath, buildHtml(project, request, readiness), StandardCharsets.UTF_8);

            record.setFileName(fileName);
            record.setFileUrl(filePath.toString());
            record.setFileSize(Files.size(filePath));
            record.setStatus("GENERATED");
            record.setFailureReason(null);
            record.setUpdateTime(LocalDateTime.now());
            exportRecordMapper.updateById(record);

            notificationService.notifyUser(current.getId(), projectId, "导出完成",
                    "项目「" + project.getProjectName() + "」导出完成，可下载最新 HTML 成果。",
                    "EXPORT_COMPLETE", "/api/exports/" + record.getId() + "/download");
            return toVO(record, project, current);
        } catch (IOException exception) {
            record.setStatus("FAILED");
            record.setFailureReason(exception.getMessage());
            record.setUpdateTime(LocalDateTime.now());
            exportRecordMapper.updateById(record);
            throw new BizException(500, "导出失败：" + exception.getMessage());
        }
    }

    public List<ExportRecordVO> history(Long projectId) {
        Project project = projectService.getProject(projectId);
        return exportRecordMapper.selectList(new LambdaQueryWrapper<ExportRecord>()
                        .eq(ExportRecord::getProjectId, project.getId())
                        .orderByDesc(ExportRecord::getCreateTime)
                        .orderByDesc(ExportRecord::getId))
                .stream()
                .map(record -> toVO(record, project, null))
                .toList();
    }

    public ResponseEntity<Resource> download(Long exportId) {
        ExportRecord record = getRecord(exportId);
        requireExportAccess(record);
        if (!StringUtils.hasText(record.getFileUrl())) {
            throw new BizException(404, "导出文件不存在");
        }
        try {
            Resource resource = new UrlResource(Paths.get(record.getFileUrl()).toUri());
            if (!resource.exists()) {
                throw new BizException(404, "导出文件不存在");
            }
            String fileName = StringUtils.hasText(record.getFileName()) ? record.getFileName() : "export.html";
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .contentLength(record.getFileSize() == null ? resource.contentLength() : record.getFileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (MalformedURLException exception) {
            throw new BizException(500, "导出文件路径无效");
        } catch (IOException exception) {
            throw new BizException(500, "读取导出文件失败：" + exception.getMessage());
        }
    }

    private ExportRecord getRecord(Long exportId) {
        ExportRecord record = exportId == null ? null : exportRecordMapper.selectById(exportId);
        if (record == null) {
            throw new BizException(404, "导出记录不存在");
        }
        return record;
    }

    private void requireExportAccess(ExportRecord record) {
        projectService.getProject(record.getProjectId());
    }

    private String buildHtml(Project project, ExportRequestDTO request, CompletenessVO readiness) {
        List<ProjectSection> sections = loadSections(project.getId(), request);
        Set<Long> sectionIds = sections.stream().map(ProjectSection::getId).collect(java.util.stream.Collectors.toSet());
        List<SectionContent> latestContents = sectionIds.isEmpty() ? List.of() : sectionContentMapper.selectList(new LambdaQueryWrapper<SectionContent>()
                .eq(SectionContent::getProjectId, project.getId())
                .in(SectionContent::getSectionId, sectionIds)
                .orderByAsc(SectionContent::getSectionId)
                .orderByDesc(SectionContent::getVersionNo)
                .orderByDesc(SectionContent::getId));
        latestContents = latestContents.stream()
                .collect(java.util.stream.Collectors.toMap(SectionContent::getSectionId, content -> content, (left, right) -> left))
                .values()
                .stream()
                .toList();
        List<Task> tasks = includeTasks(request) ? taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, project.getId())
                .orderByAsc(Task::getDueDate)
                .orderByDesc(Task::getCreateTime)) : List.of();
        List<ProgressRecord> progressRecords = includeProgress(request) ? progressRecordMapper.selectList(new LambdaQueryWrapper<ProgressRecord>()
                .eq(ProgressRecord::getProjectId, project.getId())
                .orderByDesc(ProgressRecord::getSubmitTime)
                .orderByDesc(ProgressRecord::getId)) : List.of();
        List<AiRecord> aiRecords = includeAi(request) ? aiRecordMapper.selectList(new LambdaQueryWrapper<AiRecord>()
                .eq(AiRecord::getProjectId, project.getId())
                .orderByDesc(AiRecord::getCreateTime)
                .orderByDesc(AiRecord::getId)) : List.of();
        CompletenessVO completeness = includeStatistics(request) ? readiness : null;
        ContributionVO contribution = includeStatistics(request) ? statisticsService.contribution(project.getId()) : null;
        RiskTrendVO riskTrend = includeStatistics(request) ? statisticsService.riskTrend(project.getId()) : null;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<title>").append(escape(project.getProjectName())).append(" 导出</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:32px;color:#222;line-height:1.6;}")
                .append("h1,h2,h3{color:#111;}table{width:100%;border-collapse:collapse;margin:12px 0 24px;}")
                .append("th,td{border:1px solid #d9d9d9;padding:8px;vertical-align:top;text-align:left;}")
                .append("th{background:#f5f5f5;}pre{white-space:pre-wrap;word-break:break-word;background:#fafafa;padding:12px;border:1px solid #eee;}")
                .append(".meta{margin-bottom:24px;padding:16px;background:#fafafa;border:1px solid #eee;}")
                .append("</style></head><body>");
        html.append("<h1>").append(escape(project.getProjectName())).append(" 导出报告</h1>");
        html.append("<div class=\"meta\">")
                .append("<div>项目编码：").append(escape(project.getProjectCode())).append("</div>")
                .append("<div>状态：").append(escape(project.getStatus())).append("</div>")
                .append("<div>完整度检查：").append(escape(readiness == null ? "未计算" : String.valueOf(readiness.getScore()))).append("</div>")
                .append("<div>导出时间：").append(escape(LocalDateTime.now().toString())).append("</div>")
                .append("</div>");

        html.append("<h2>章节内容</h2><table><thead><tr><th>章节</th><th>状态</th><th>标题</th><th>正文</th></tr></thead><tbody>");
        for (ProjectSection section : sections) {
            SectionContent content = latestContents.stream()
                    .filter(item -> Objects.equals(item.getSectionId(), section.getId()))
                    .findFirst()
                    .orElse(null);
            html.append("<tr><td>").append(escape(section.getSectionName()))
                    .append("</td><td>").append(escape(section.getStatus()))
                    .append("</td><td>").append(escape(content == null ? "" : content.getTitle()))
                    .append("</td><td>").append(formatBody(content == null ? "" : content.getBody()))
                    .append("</td></tr>");
        }
        html.append("</tbody></table>");

        if (includeTasks(request)) {
            html.append("<h2>任务清单</h2><table><thead><tr><th>标题</th><th>状态</th><th>优先级</th><th>负责人</th><th>截止日期</th><th>阻塞原因</th></tr></thead><tbody>");
            for (Task task : tasks) {
                SysUser assignee = task.getAssigneeId() == null ? null : sysUserMapper.selectById(task.getAssigneeId());
                html.append("<tr><td>").append(escape(task.getTitle()))
                        .append("</td><td>").append(escape(task.getStatus()))
                        .append("</td><td>").append(escape(task.getPriority()))
                        .append("</td><td>").append(escape(assignee == null ? "" : assignee.getRealName()))
                        .append("</td><td>").append(escape(task.getDueDate() == null ? "" : task.getDueDate().toString()))
                        .append("</td><td>").append(escape(task.getBlockReason()))
                        .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        if (includeProgress(request)) {
            html.append("<h2>进度记录</h2><table><thead><tr><th>成员</th><th>周期</th><th>提交状态</th><th>完成内容</th><th>问题</th><th>下周计划</th></tr></thead><tbody>");
            for (ProgressRecord record : progressRecords) {
                SysUser user = record.getUserId() == null ? null : sysUserMapper.selectById(record.getUserId());
                html.append("<tr><td>").append(escape(user == null ? "" : user.getRealName()))
                        .append("</td><td>").append(escape(record.getReportPeriod()))
                        .append("</td><td>").append(escape(record.getSubmitStatus()))
                        .append("</td><td>").append(escape(record.getCompletedWork()))
                        .append("</td><td>").append(escape(record.getProblems()))
                        .append("</td><td>").append(escape(record.getNextPlan()))
                        .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        if (includeAi(request)) {
            html.append("<h2>AI 记录</h2><table><thead><tr><th>类型</th><th>风险等级</th><th>生成时间</th><th>结果</th></tr></thead><tbody>");
            for (AiRecord record : aiRecords) {
                html.append("<tr><td>").append(escape(record.getType()))
                        .append("</td><td>").append(escape(record.getRiskLevel()))
                        .append("</td><td>").append(escape(record.getCreateTime() == null ? "" : record.getCreateTime().toString()))
                        .append("</td><td><pre>").append(escape(record.getResult()))
                        .append("</pre></td></tr>");
            }
            html.append("</tbody></table>");
        }

        if (includeStatistics(request) && completeness != null && contribution != null && riskTrend != null) {
            html.append("<h2>统计摘要</h2>");
            html.append("<table><thead><tr><th>总体得分</th><th>章节完成</th><th>审核通过</th><th>任务完成</th><th>缺陷闭环</th><th>总结/导出就绪</th></tr></thead><tbody><tr>")
                    .append("<td>").append(escape(String.valueOf(completeness.getScore()))).append("</td>")
                    .append("<td>").append(escape(String.valueOf(completeness.getRequiredSectionScore()))).append("</td>")
                    .append("<td>").append(escape(String.valueOf(completeness.getReviewPassScore()))).append("</td>")
                    .append("<td>").append(escape(String.valueOf(completeness.getTaskCompletionScore()))).append("</td>")
                    .append("<td>").append(escape(String.valueOf(completeness.getDefectClosureScore()))).append("</td>")
                    .append("<td>").append(escape(String.valueOf(completeness.getExportReadinessScore()))).append("</td>")
                    .append("</tr></tbody></table>");
            html.append("<h3>扣分原因</h3><ul>");
            for (String reason : completeness.getDeductionReasons()) {
                html.append("<li>").append(escape(reason)).append("</li>");
            }
            html.append("</ul>");

            html.append("<h3>成员贡献</h3><table><thead><tr><th>成员</th><th>角色</th><th>总分</th><th>任务</th><th>文档</th><th>进度</th><th>评审</th><th>缺陷</th></tr></thead><tbody>");
            for (ContributionVO.MemberContribution member : contribution.getMembers()) {
                html.append("<tr><td>").append(escape(member.getRealName()))
                        .append("</td><td>").append(escape(member.getRoleName()))
                        .append("</td><td>").append(escape(String.valueOf(member.getTotalScore())))
                        .append("</td><td>").append(escape(String.valueOf(member.getTaskScore())))
                        .append("</td><td>").append(escape(String.valueOf(member.getDocumentScore())))
                        .append("</td><td>").append(escape(String.valueOf(member.getReportScore())))
                        .append("</td><td>").append(escape(String.valueOf(member.getReviewScore())))
                        .append("</td><td>").append(escape(String.valueOf(member.getDefectScore())))
                        .append("</td></tr>");
            }
            html.append("</tbody></table>");

            html.append("<h3>风险趋势</h3><table><thead><tr><th>时间</th><th>等级</th><th>分值</th><th>逾期任务</th><th>阻塞任务</th><th>缺失报告</th><th>驳回章节</th><th>趋势</th></tr></thead><tbody>");
            for (RiskTrendVO.RiskSnapshotItem item : riskTrend.getSnapshots()) {
                html.append("<tr><td>").append(escape(item.getSnapshotTime() == null ? "" : item.getSnapshotTime().toString()))
                        .append("</td><td>").append(escape(item.getRiskLevel()))
                        .append("</td><td>").append(escape(String.valueOf(item.getRiskScore())))
                        .append("</td><td>").append(escape(String.valueOf(item.getTaskDelayCount())))
                        .append("</td><td>").append(escape(String.valueOf(item.getBlockedTaskCount())))
                        .append("</td><td>").append(escape(String.valueOf(item.getMissingReportCount())))
                        .append("</td><td>").append(escape(String.valueOf(item.getRejectedSectionCount())))
                        .append("</td><td>").append(escape(item.getTrend()))
                        .append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private List<ProjectSection> loadSections(Long projectId, ExportRequestDTO request) {
        List<ProjectSection> sections = projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, projectId)
                .orderByAsc(ProjectSection::getSortOrder)
                .orderByAsc(ProjectSection::getId));
        if (request == null || request.getIncludeSections() == null || request.getIncludeSections().isEmpty()) {
            return sections;
        }
        Set<Long> includeIds = new LinkedHashSet<>(request.getIncludeSections());
        return sections.stream().filter(section -> includeIds.contains(section.getId())).toList();
    }

    private String stableFileName(Project project, Long recordId, LocalDateTime time) {
        String slug = normalizeFileToken(project.getProjectCode());
        if (!StringUtils.hasText(slug)) {
            slug = "project-" + project.getId();
        }
        return slug + "-export-" + recordId + "-" + FILE_STAMP.format(time) + ".html";
    }

    private String normalizeScope(ExportRequestDTO request) {
        String scope = request == null ? null : request.getExportScope();
        return StringUtils.hasText(scope) ? scope.trim().toUpperCase(Locale.ROOT) : "FULL";
    }

    private boolean includeTasks(ExportRequestDTO request) {
        return request == null || request.getIncludeTasks() == null || request.getIncludeTasks();
    }

    private boolean includeProgress(ExportRequestDTO request) {
        return request == null || request.getIncludeProgress() == null || request.getIncludeProgress();
    }

    private boolean includeAi(ExportRequestDTO request) {
        return request == null || request.getIncludeAi() == null || request.getIncludeAi();
    }

    private boolean includeStatistics(ExportRequestDTO request) {
        return request == null || request.getIncludeStatistics() == null || request.getIncludeStatistics();
    }

    private String normalizeFileToken(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        return normalized.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
    }

    private ExportRecordVO toVO(ExportRecord record, Project cachedProject, SysUser cachedCreator) {
        Project project = cachedProject != null ? cachedProject : projectService.getProject(record.getProjectId());
        SysUser creator = cachedCreator != null ? cachedCreator
                : (record.getCreatorId() == null ? null : sysUserMapper.selectById(record.getCreatorId()));
        ExportRecordVO vo = new ExportRecordVO();
        vo.setId(record.getId());
        vo.setProjectId(record.getProjectId());
        vo.setProjectName(project == null ? "" : project.getProjectName());
        vo.setCreatorId(record.getCreatorId());
        vo.setCreatorName(creator == null ? "" : creator.getRealName());
        vo.setExportScope(record.getExportScope());
        vo.setFileName(record.getFileName());
        vo.setFileUrl("/api/exports/" + record.getId() + "/download");
        vo.setFileSize(record.getFileSize());
        vo.setStatus(record.getStatus());
        vo.setFailureReason(record.getFailureReason());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String formatBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String[] lines = body.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("#### ")) {
                sb.append("<h4>").append(escape(trimmed.substring(5))).append("</h4>");
                i++;
                continue;
            } else if (trimmed.startsWith("### ")) {
                sb.append("<h3>").append(escape(trimmed.substring(4))).append("</h3>");
                i++;
                continue;
            } else if (trimmed.startsWith("## ")) {
                sb.append("<h2>").append(escape(trimmed.substring(3))).append("</h2>");
                i++;
                continue;
            } else if (trimmed.startsWith("# ")) {
                sb.append("<h1>").append(escape(trimmed.substring(2))).append("</h1>");
                i++;
                continue;
            }

            if (trimmed.startsWith("```plantuml")) {
                StringBuilder umlSource = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    umlSource.append(lines[i]).append("\n");
                    i++;
                }
                if (i < lines.length) i++;
                String source = umlSource.toString().trim();
                String encoded = encodePlantUml(source);
                if (StringUtils.hasText(encoded)) {
                    sb.append("<div style=\"margin: 16px 0; text-align: center;\">")
                      .append("<img src=\"https://www.plantuml.com/plantuml/svg/").append(encoded)
                      .append("\" alt=\"PlantUML Diagram\" style=\"max-width: 100%; height: auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px; background: #fff;\"/>")
                      .append("</div>");
                } else {
                    sb.append("<pre>").append(escape(source)).append("</pre>");
                }
                continue;
            }

            if (trimmed.startsWith("```")) {
                StringBuilder codeBlock = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    codeBlock.append(lines[i]).append("\n");
                    i++;
                }
                if (i < lines.length) i++;
                sb.append("<pre>").append(escape(codeBlock.toString().trim())).append("</pre>");
                continue;
            }

            if (trimmed.startsWith("|")) {
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && lines[i].trim().startsWith("|")) {
                    tableLines.add(lines[i]);
                    i++;
                }
                sb.append(formatMarkdownTable(tableLines));
                continue;
            }

            if (StringUtils.hasText(trimmed)) {
                StringBuilder para = new StringBuilder(trimmed);
                i++;
                while (i < lines.length && StringUtils.hasText(lines[i].trim()) && !lines[i].trim().startsWith("|") && !lines[i].trim().startsWith("```") && !lines[i].trim().startsWith("#")) {
                    para.append(" ").append(lines[i].trim());
                    i++;
                }
                sb.append("<p>").append(escape(para.toString())).append("</p>");
            } else {
                i++;
            }
        }
        return sb.toString();
    }

    private String formatMarkdownTable(List<String> tableLines) {
        if (tableLines.size() < 2) {
            StringBuilder sb = new StringBuilder();
            for (String l : tableLines) {
                sb.append("<p>").append(escape(l)).append("</p>");
            }
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width:100%; border-collapse:collapse; margin:16px 0;\">");
        
        boolean hasHeader = false;
        int headerColsCount = 0;
        
        for (int idx = 0; idx < tableLines.size(); idx++) {
            String line = tableLines.get(idx);
            String[] cells = line.split("\\|");
            
            List<String> cleanedCells = new ArrayList<>();
            int startPos = line.startsWith("|") ? 1 : 0;
            int endPos = cells.length;
            for (int k = startPos; k < endPos; k++) {
                cleanedCells.add(cells[k].trim());
            }
            
            boolean isSeparator = true;
            for (String cell : cleanedCells) {
                if (!cell.matches(":?-+:?")) {
                    isSeparator = false;
                    break;
                }
            }
            if (isSeparator && cleanedCells.size() > 0) {
                continue;
            }
            
            if (!hasHeader) {
                sb.append("<thead><tr>");
                for (String cell : cleanedCells) {
                    sb.append("<th style=\"border:1px solid #d9d9d9; padding:8px; background:#f5f5f5; font-weight:bold;\">")
                      .append(escape(cell)).append("</th>");
                }
                sb.append("</tr></thead><tbody>");
                hasHeader = true;
                headerColsCount = cleanedCells.size();
            } else {
                sb.append("<tr>");
                for (int c = 0; c < headerColsCount; c++) {
                    String cellVal = c < cleanedCells.size() ? cleanedCells.get(c) : "";
                    sb.append("<td style=\"border:1px solid #d9d9d9; padding:8px;\">")
                      .append(escape(cellVal)).append("</td>");
                }
                sb.append("</tr>");
            }
        }
        if (hasHeader) {
            sb.append("</tbody>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private static String encodePlantUml(String source) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION, true);
            byte[] input = source.getBytes(StandardCharsets.UTF_8);
            deflater.setInput(input);
            deflater.finish();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            deflater.end();
            byte[] compressed = baos.toByteArray();
            return encode64(compressed);
        } catch (Exception e) {
            return "";
        }
    }

    private static String encode64(byte[] data) {
        StringBuilder r = new StringBuilder();
        int len = data.length;
        for (int i = 0; i < len; i += 3) {
            if (i + 2 < len) {
                append3bytes(r, data[i] & 0xFF, data[i + 1] & 0xFF, data[i + 2] & 0xFF);
            } else if (i + 1 < len) {
                append3bytes(r, data[i] & 0xFF, data[i + 1] & 0xFF, 0);
            } else {
                append3bytes(r, data[i] & 0xFF, 0, 0);
            }
        }
        return r.toString();
    }

    private static void append3bytes(StringBuilder r, int b1, int b2, int b3) {
        int c1 = b1 >> 2;
        int c2 = ((b1 & 0x3) << 4) | (b2 >> 4);
        int c3 = ((b2 & 0xF) << 2) | (b3 >> 6);
        int c4 = b3 & 0x3F;
        r.append(encodeByte(c1));
        r.append(encodeByte(c2));
        r.append(encodeByte(c3));
        r.append(encodeByte(c4));
    }

    private static char encodeByte(int b) {
        if (b < 10) {
            return (char) ('0' + b);
        }
        b -= 10;
        if (b < 26) {
            return (char) ('A' + b);
        }
        b -= 26;
        if (b < 26) {
            return (char) ('a' + b);
        }
        b -= 26;
        if (b == 0) {
            return '-';
        }
        if (b == 1) {
            return '_';
        }
        return '?';
    }
}
