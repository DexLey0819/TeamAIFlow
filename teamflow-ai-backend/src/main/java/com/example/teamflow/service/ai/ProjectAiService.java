package com.example.teamflow.service.ai;

import com.example.teamflow.ai.AiClient;
import com.example.teamflow.entity.AiRecord;
import com.example.teamflow.entity.ExportRecord;
import com.example.teamflow.entity.ProgressRecord;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RiskSnapshot;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.RiskSnapshotMapper;
import com.example.teamflow.service.ProjectService;
import com.example.teamflow.vo.AiRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.teamflow.service.ai.AiTextSupport.currentPeriod;
import static com.example.teamflow.service.ai.AiTextSupport.firstNonBlank;
import static com.example.teamflow.service.ai.AiTextSupport.normalize;
import static com.example.teamflow.service.ai.AiTextSupport.nullSafe;
import static com.example.teamflow.service.ai.AiTextSupport.toInt;
import static com.example.teamflow.service.ai.AiTextSupport.truncate;
import static com.example.teamflow.service.ai.AiTextSupport.valueOf;

@Service
@RequiredArgsConstructor
public class ProjectAiService {
    private final AiClient aiClient;
    private final ProjectService projectService;
    private final AiContextBuilder aiContextBuilder;
    private final AiRecordService aiRecordService;
    private final RiskSnapshotMapper riskSnapshotMapper;

    public AiRecordVO weeklyReport(Long projectId) {
        return generate(projectId, "WEEKLY_REPORT");
    }

    public AiRecordVO riskAnalysis(Long projectId) {
        return generate(projectId, "RISK_ANALYSIS");
    }

    public AiRecordVO documentCheck(Long projectId) {
        return generate(projectId, AiRecord.TYPE_DOC_CHECK);
    }

    public AiRecordVO summaryReport(Long projectId) {
        return generate(projectId, "SUMMARY_REPORT");
    }

    private AiRecordVO generate(Long projectId, String type) {
        Project project = projectService.getProject(projectId);
        projectService.ensureManager(project);
        ProjectAiContext context = aiContextBuilder.build(project);
        String prompt = buildPrompt(project, context, type);
        String result;
        String source;
        String modelName;
        if (aiClient.enabled()) {
            try {
                result = aiClient.chat(prompt);
                if (StringUtils.hasText(result)) {
                    source = AiRecord.SOURCE_ZHIPU_GLM;
                    modelName = AiRecord.DEFAULT_MODEL_NAME;
                } else {
                    result = fallback(project, context, type);
                    source = AiRecord.SOURCE_LOCAL_FALLBACK;
                    modelName = "local-fallback";
                }
            } catch (Exception exception) {
                result = fallback(project, context, type)
                        + "\n\n说明：智谱 GLM 调用失败，已切换为本地兜底结果。原因："
                        + exception.getMessage();
                source = AiRecord.SOURCE_LOCAL_FALLBACK;
                modelName = "local-fallback";
            }
        } else {
            result = fallback(project, context, type);
            source = AiRecord.SOURCE_LOCAL_FALLBACK;
            modelName = "local-fallback";
        }
        String riskLevel = "RISK_ANALYSIS".equals(type) ? inferRiskLevel(context) : null;
        AiRecord record = aiRecordService.saveGeneratedRecord(projectId, type, prompt, result, source, modelName, riskLevel);
        if ("RISK_ANALYSIS".equals(type)) {
            saveRiskSnapshot(projectId, record, context, result, riskLevel, record.getCreateTime());
        }
        return aiRecordService.toVO(record);
    }

    private String buildPrompt(Project project, ProjectAiContext context, String type) {
        long done = context.getTasks().stream().filter(task -> "DONE".equalsIgnoreCase(task.getStatus())).count();
        long blocked = countBlockedTasks(context.getTasks());
        long overdue = countOverdueTasks(context.getTasks());
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 TeamFlowAI 的项目分析助手，默认使用智谱 GLM 生成结果。\n")
                .append("硬性约束：只基于已提供数据分析，不编造未出现内容；若数据不足，请明确写出“数据不足”。\n")
                .append("输出类型：").append(type).append("\n\n")
                .append("【项目信息】\n")
                .append("项目名称：").append(nullSafe(project.getProjectName())).append("\n")
                .append("项目编号：").append(nullSafe(project.getProjectCode())).append("\n")
                .append("项目状态：").append(nullSafe(project.getStatus())).append("\n")
                .append("开始日期：").append(valueOf(project.getStartDate())).append("\n")
                .append("结束日期：").append(valueOf(project.getEndDate())).append("\n")
                .append("项目描述：").append(nullSafe(project.getDescription())).append("\n")
                .append("项目文件数量：").append(context.getFileCount()).append("\n\n")
                .append("【角色信息】\n")
                .append(renderRoles(context.getRoles())).append("\n\n")
                .append("【成员信息】\n")
                .append(renderMembers(context)).append("\n\n")
                .append("【章节信息】\n")
                .append(renderSections(context)).append("\n\n")
                .append("【任务信息】\n")
                .append("任务总数：").append(context.getTasks().size())
                .append("，已完成：").append(done)
                .append("，阻塞：").append(blocked)
                .append("，逾期未完成：").append(overdue).append("\n")
                .append(renderTasks(context)).append("\n\n")
                .append("【进度记录】\n")
                .append(renderProgressRecords(context)).append("\n\n")
                .append("【历史风险快照】\n")
                .append(renderRiskSnapshots(context.getRiskSnapshots())).append("\n\n")
                .append("【导出记录】\n")
                .append(renderExportRecords(context.getExportRecords())).append("\n");

        if ("WEEKLY_REPORT".equals(type)) {
            prompt.append("\n请输出：本周完成情况、成员协作观察、问题与风险、下周建议。");
        } else if ("RISK_ANALYSIS".equals(type)) {
            prompt.append("\n请输出：风险等级、风险依据、影响范围、缓解建议。");
        } else if (AiRecord.TYPE_DOC_CHECK.equals(type)) {
            prompt.append("\n请输出：文档覆盖度检查、缺失点、质量风险、改进建议。");
        } else if ("SUMMARY_REPORT".equals(type)) {
            prompt.append("\n请输出：项目概况、核心成果、当前瓶颈、后续建议。");
        }
        return prompt.toString();
    }

    private String fallback(Project project, ProjectAiContext context, String type) {
        long done = context.getTasks().stream().filter(task -> "DONE".equalsIgnoreCase(task.getStatus())).count();
        long blocked = countBlockedTasks(context.getTasks());
        long overdue = countOverdueTasks(context.getTasks());
        long submitted = context.getProgressRecords().stream()
                .filter(record -> Set.of("NORMAL", "LATE", "SUPPLEMENTED")
                        .contains(normalize(record.getSubmitStatus())))
                .count();
        if ("WEEKLY_REPORT".equals(type)) {
            return "一、本周项目进展\n"
                    + project.getProjectName() + " 当前共有 " + context.getTasks().size() + " 个任务，已完成 " + done + " 个，已提交进度记录 " + submitted + " 条。\n\n"
                    + "二、成员与文档情况\n当前已配置 " + context.getRoles().size() + " 个角色、" + context.getMembers().size() + " 名成员，项目文件 " + context.getFileCount() + " 份，可据此持续补充周报证据。\n\n"
                    + "三、存在的问题\n当前阻塞任务 " + blocked + " 个，逾期未完成任务 " + overdue + " 个，需要组长重点跟进。\n\n"
                    + "四、下周工作建议\n建议优先处理阻塞任务，补充进度记录，并在每周例会中同步风险。";
        }
        if ("RISK_ANALYSIS".equals(type)) {
            String level = inferRiskLevel(context);
            return "风险等级：" + level + "\n"
                    + "风险原因：阻塞任务 " + blocked + " 个，逾期任务 " + overdue + " 个。\n"
                    + "影响分析：如果阻塞任务持续存在，可能影响章节交付和项目按期验收。\n"
                    + "改进建议：明确负责人、拆分任务粒度、补充周进度记录，并由项目经理或组长进行阶段检查。";
        }
        if (AiRecord.TYPE_DOC_CHECK.equals(type)) {
            return "一、文档覆盖概览\n当前项目文件数量为 " + context.getFileCount() + "，章节数量为 " + context.getSections().size() + "，最近导出记录 " + context.getExportRecords().size() + " 条。\n\n"
                    + "二、发现的问题\n若关键章节状态仍非完成，或缺少对应进度记录与导出产物，应视为文档覆盖不足。\n\n"
                    + "三、建议\n请结合章节状态、进度记录和导出结果逐项核对需求、设计、实现、测试与验收材料。";
        }
        return "一、项目背景\n" + project.getProjectName() + " 面向项目小组协作场景。\n\n"
                + "二、系统目标\n提升任务分工、进度可视化、文档归档和 AI 汇报效率。\n\n"
                + "三、主要功能\n用户认证、项目管理、成员管理、任务看板、进度记录、文档管理、AI 分析和统计看板。\n\n"
                + "四、技术栈\nSpring Boot、MyBatis-Plus、MySQL、Vue3、Element Plus 和智谱 GLM。\n\n"
                + "五、项目成果\n当前任务完成率约 " + (context.getTasks().isEmpty() ? 0 : Math.round(done * 100.0 / context.getTasks().size())) + "%，可支撑课程演示和后续扩展。";
    }

    private String inferRiskLevel(ProjectAiContext context) {
        long blocked = countBlockedTasks(context.getTasks());
        long overdue = countOverdueTasks(context.getTasks());
        long rejectedSections = context.getSections().stream()
                .map(ProjectSection::getStatus)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .filter(status -> status.contains("REJECT"))
                .count();
        if (blocked >= 2 || overdue >= 2 || rejectedSections >= 1) {
            return "HIGH";
        }
        if (blocked == 1 || overdue == 1 || context.getTasks().stream().noneMatch(task -> "DONE".equalsIgnoreCase(task.getStatus()))) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void saveRiskSnapshot(Long projectId, AiRecord record, ProjectAiContext context, String result, String riskLevel, LocalDateTime now) {
        LocalDateTime snapshotTime = now == null ? LocalDateTime.now() : now;
        long overdue = countOverdueTasks(context.getTasks());
        long blocked = countBlockedTasks(context.getTasks());
        long submittedUsers = context.getProgressRecords().stream()
                .filter(pr -> currentPeriod().equals(normalize(pr.getReportPeriod())))
                .filter(pr -> Set.of("NORMAL", "LATE", "SUPPLEMENTED")
                        .contains(normalize(pr.getSubmitStatus())))
                .map(ProgressRecord::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long missingReports = Math.max(0, context.getMembers().size() - submittedUsers);
        long rejectedSections = context.getSections().stream()
                .map(ProjectSection::getStatus)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .filter(status -> status.contains("REJECT"))
                .count();

        RiskSnapshot snapshot = new RiskSnapshot();
        snapshot.setProjectId(projectId);
        snapshot.setAiRecordId(record.getId());
        snapshot.setRiskLevel(riskLevel);
        snapshot.setRiskScore(calculateRiskScore(overdue, blocked, missingReports, rejectedSections));
        snapshot.setTaskDelayCount(toInt(overdue));
        snapshot.setBlockedTaskCount(toInt(blocked));
        snapshot.setMissingReportCount(toInt(missingReports));
        snapshot.setRejectedSectionCount(toInt(rejectedSections));
        snapshot.setTrend("HIGH".equals(riskLevel) ? "UP" : "STABLE");
        snapshot.setSummary(truncate(result, 500));
        snapshot.setSnapshotTime(snapshotTime);
        snapshot.setCreateTime(snapshotTime);
        snapshot.setUpdateTime(snapshotTime);
        riskSnapshotMapper.insert(snapshot);
    }

    private BigDecimal calculateRiskScore(long overdue, long blocked, long missingReports, long rejectedSections) {
        BigDecimal score = BigDecimal.valueOf(overdue)
                .multiply(BigDecimal.valueOf(25))
                .add(BigDecimal.valueOf(blocked).multiply(BigDecimal.valueOf(30)))
                .add(BigDecimal.valueOf(missingReports).multiply(BigDecimal.valueOf(10)))
                .add(BigDecimal.valueOf(rejectedSections).multiply(BigDecimal.valueOf(20)));
        return score.min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private long countBlockedTasks(List<Task> tasks) {
        return tasks.stream().filter(task -> "BLOCKED".equalsIgnoreCase(task.getStatus())).count();
    }

    private long countOverdueTasks(List<Task> tasks) {
        LocalDate today = LocalDate.now();
        return tasks.stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(today))
                .filter(task -> !"DONE".equalsIgnoreCase(task.getStatus()))
                .count();
    }

    private String renderRoles(List<ProjectRole> roles) {
        if (roles.isEmpty()) {
            return "无角色数据";
        }
        return roles.stream()
                .map(role -> "- " + role.getRoleName()
                        + "（code=" + nullSafe(role.getRoleCode())
                        + "，current/max=" + valueOf(role.getCurrentCount()) + "/" + valueOf(role.getMaxCount())
                        + "，required=" + valueOf(role.getRequiredFlag())
                        + "，responsibility=" + nullSafe(role.getResponsibility()) + "）")
                .collect(Collectors.joining("\n"));
    }

    private String renderMembers(ProjectAiContext context) {
        if (context.getMembers().isEmpty()) {
            return "无成员数据";
        }
        return context.getMembers().stream()
                .map(member -> {
                    SysUser user = context.getUsers().get(member.getUserId());
                    ProjectRole role = context.getRolesById().get(member.getProjectRoleId());
                    String displayName = user == null ? "user#" + member.getUserId() : firstNonBlank(user.getRealName(), user.getUsername());
                    return "- " + displayName
                            + "（role=" + firstNonBlank(member.getMemberTitle(), member.getMemberRole(), role == null ? null : role.getRoleName())
                            + "，joinSource=" + nullSafe(member.getJoinSource()) + "）";
                })
                .collect(Collectors.joining("\n"));
    }

    private String renderSections(ProjectAiContext context) {
        if (context.getSections().isEmpty()) {
            return "无章节数据";
        }
        return context.getSections().stream()
                .map(section -> {
                    ProjectRole ownerRole = context.getRolesById().get(section.getOwnerRoleId());
                    return "- " + section.getSectionName()
                            + "（code=" + nullSafe(section.getSectionCode())
                            + "，status=" + nullSafe(section.getStatus())
                            + "，ownerRole=" + (ownerRole == null ? "未设置" : ownerRole.getRoleName()) + "）";
                })
                .collect(Collectors.joining("\n"));
    }

    private String renderTasks(ProjectAiContext context) {
        if (context.getTasks().isEmpty()) {
            return "无任务数据";
        }
        return context.getTasks().stream()
                .limit(12)
                .map(task -> {
                    SysUser assignee = context.getUsers().get(task.getAssigneeId());
                    String assigneeName = assignee == null ? "未分配" : firstNonBlank(assignee.getRealName(), assignee.getUsername());
                    return "- " + task.getTitle()
                            + "（status=" + nullSafe(task.getStatus())
                            + "，priority=" + nullSafe(task.getPriority())
                            + "，assignee=" + assigneeName
                            + "，due=" + valueOf(task.getDueDate())
                            + "，blockReason=" + nullSafe(task.getBlockReason()) + "）";
                })
                .collect(Collectors.joining("\n"));
    }

    private String renderProgressRecords(ProjectAiContext context) {
        if (context.getProgressRecords().isEmpty()) {
            return "无进度记录";
        }
        return context.getProgressRecords().stream()
                .limit(8)
                .map(record -> {
                    SysUser user = context.getUsers().get(record.getUserId());
                    ProjectRole role = context.getRolesById().get(record.getProjectRoleId());
                    return "- 周期=" + firstNonBlank(record.getReportPeriod(), valueOf(record.getWeekStart()) + "~" + valueOf(record.getWeekEnd()))
                            + "，提交人=" + (user == null ? "user#" + record.getUserId() : firstNonBlank(user.getRealName(), user.getUsername()))
                            + "，角色=" + (role == null ? "未知角色" : role.getRoleName())
                            + "，状态=" + nullSafe(record.getSubmitStatus())
                            + "，完成=" + truncate(record.getCompletedWork(), 100)
                            + "，问题=" + truncate(record.getProblems(), 80)
                            + "，下周=" + truncate(record.getNextPlan(), 80);
                })
                .collect(Collectors.joining("\n"));
    }

    private String renderRiskSnapshots(List<RiskSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return "无历史风险快照";
        }
        return snapshots.stream()
                .limit(6)
                .map(snapshot -> "- time=" + valueOf(snapshot.getSnapshotTime())
                        + "，level=" + nullSafe(snapshot.getRiskLevel())
                        + "，score=" + valueOf(snapshot.getRiskScore())
                        + "，delay=" + valueOf(snapshot.getTaskDelayCount())
                        + "，blocked=" + valueOf(snapshot.getBlockedTaskCount())
                        + "，summary=" + truncate(snapshot.getSummary(), 120))
                .collect(Collectors.joining("\n"));
    }

    private String renderExportRecords(List<ExportRecord> exports) {
        if (exports.isEmpty()) {
            return "无导出记录";
        }
        return exports.stream()
                .limit(8)
                .map(record -> "- " + nullSafe(record.getFileName())
                        + "（scope=" + nullSafe(record.getExportScope())
                        + "，status=" + nullSafe(record.getStatus())
                        + "，size=" + valueOf(record.getFileSize())
                        + "，failure=" + truncate(record.getFailureReason(), 60) + "）")
                .collect(Collectors.joining("\n"));
    }
}
