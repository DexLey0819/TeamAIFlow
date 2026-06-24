package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.entity.AiRecord;
import com.example.teamflow.entity.ProgressRecord;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.RiskSnapshot;
import com.example.teamflow.entity.SectionComment;
import com.example.teamflow.entity.SectionContent;
import com.example.teamflow.entity.SectionReview;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.AiRecordMapper;
import com.example.teamflow.mapper.ExportRecordMapper;
import com.example.teamflow.mapper.ProgressRecordMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.RiskSnapshotMapper;
import com.example.teamflow.mapper.SectionCommentMapper;
import com.example.teamflow.mapper.SectionContentMapper;
import com.example.teamflow.mapper.SectionReviewMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskMapper;
import com.example.teamflow.vo.CompletenessVO;
import com.example.teamflow.vo.ContributionVO;
import com.example.teamflow.vo.RiskTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {
    private static final BigDecimal REQUIRED_SECTION_WEIGHT = BigDecimal.valueOf(30);
    private static final BigDecimal REVIEW_PASS_WEIGHT = BigDecimal.valueOf(20);
    private static final BigDecimal TASK_COMPLETION_WEIGHT = BigDecimal.valueOf(20);
    private static final BigDecimal DEFECT_CLOSURE_WEIGHT = BigDecimal.valueOf(15);
    private static final BigDecimal EXPORT_READINESS_WEIGHT = BigDecimal.valueOf(15);

    private final ProjectService projectService;
    private final ProjectSectionMapper projectSectionMapper;
    private final SectionContentMapper sectionContentMapper;
    private final SectionReviewMapper sectionReviewMapper;
    private final SectionCommentMapper sectionCommentMapper;
    private final TaskMapper taskMapper;
    private final ProgressRecordMapper progressRecordMapper;
    private final RiskSnapshotMapper riskSnapshotMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final SysUserMapper sysUserMapper;
    private final AiRecordMapper aiRecordMapper;
    private final ExportRecordMapper exportRecordMapper;

    public CompletenessVO completeness(Long projectId) {
        Project project = projectService.getProject(projectId);
        return completeness(project);
    }

    public CompletenessVO completeness(Project project) {
        ProjectStatsSnapshot snapshot = loadProjectStats(project);
        List<String> deductionReasons = new ArrayList<>();

        BigDecimal requiredSectionScore = scoreRequiredSections(snapshot, deductionReasons);
        BigDecimal reviewPassScore = scoreReviewPass(snapshot, deductionReasons);
        BigDecimal taskCompletionScore = scoreTaskCompletion(snapshot, deductionReasons);
        BigDecimal defectClosureScore = scoreDefectClosure(snapshot, deductionReasons);
        BigDecimal exportReadinessScore = scoreExportReadiness(snapshot, deductionReasons);

        CompletenessVO vo = new CompletenessVO();
        vo.setProjectId(project.getId());
        vo.setRequiredSectionScore(requiredSectionScore);
        vo.setReviewPassScore(reviewPassScore);
        vo.setTaskCompletionScore(taskCompletionScore);
        vo.setDefectClosureScore(defectClosureScore);
        vo.setExportReadinessScore(exportReadinessScore);
        vo.setScore(requiredSectionScore
                .add(reviewPassScore)
                .add(taskCompletionScore)
                .add(defectClosureScore)
                .add(exportReadinessScore)
                .setScale(2, RoundingMode.HALF_UP));
        vo.setDeductionReasons(deductionReasons);
        return vo;
    }

    public ContributionVO contribution(Long projectId) {
        Project project = projectService.getProject(projectId);
        return contribution(project);
    }

    public ContributionVO contribution(Project project) {
        ProjectStatsSnapshot snapshot = loadProjectStats(project);

        Map<Long, MemberRawScore> rawByUserId = new LinkedHashMap<>();
        for (ProjectMember member : snapshot.members) {
            MemberRawScore score = rawByUserId.computeIfAbsent(member.getUserId(), userId -> new MemberRawScore(member));
            score.roleName = StringUtils.hasText(member.getMemberTitle()) ? member.getMemberTitle() : member.getMemberRole();
        }
        if (!rawByUserId.containsKey(snapshot.project.getCreatorId()) && snapshot.project.getCreatorId() != null) {
            rawByUserId.put(snapshot.project.getCreatorId(), new MemberRawScore(null));
        }

        for (Task task : snapshot.tasks) {
            MemberRawScore score = rawByUserId.computeIfAbsent(task.getAssigneeId(), ignored -> new MemberRawScore(null));
            if ("DONE".equalsIgnoreCase(task.getStatus())) {
                score.taskRaw = score.taskRaw.add(BigDecimal.valueOf(5));
            } else if ("REVIEW".equalsIgnoreCase(task.getStatus())) {
                score.taskRaw = score.taskRaw.add(BigDecimal.valueOf(3));
            } else if ("IN_PROGRESS".equalsIgnoreCase(task.getStatus())) {
                score.taskRaw = score.taskRaw.add(BigDecimal.valueOf(2));
            } else if (task.getAssigneeId() != null) {
                score.taskRaw = score.taskRaw.add(BigDecimal.ONE);
            }
            if ("BLOCKED".equalsIgnoreCase(task.getStatus()) || StringUtils.hasText(task.getBlockReason())) {
                score.defectRaw = score.defectRaw.add("DONE".equalsIgnoreCase(task.getStatus())
                        ? BigDecimal.valueOf(2.5)
                        : BigDecimal.valueOf(0.5));
            }
        }

        for (SectionContent content : snapshot.contents) {
            MemberRawScore score = rawByUserId.computeIfAbsent(content.getEditorId(), ignored -> new MemberRawScore(null));
            score.documentRaw = score.documentRaw.add(BigDecimal.valueOf(2));
            if ("APPROVED".equalsIgnoreCase(content.getSubmitStatus())) {
                score.documentRaw = score.documentRaw.add(BigDecimal.valueOf(2));
            } else if ("REVIEWING".equalsIgnoreCase(content.getSubmitStatus())) {
                score.documentRaw = score.documentRaw.add(BigDecimal.ONE);
            }
        }

        for (ProgressRecord record : snapshot.progressRecords) {
            MemberRawScore score = rawByUserId.computeIfAbsent(record.getUserId(), ignored -> new MemberRawScore(null));
            score.reportRaw = score.reportRaw.add("NORMAL".equalsIgnoreCase(record.getSubmitStatus())
                    ? BigDecimal.valueOf(3)
                    : BigDecimal.valueOf(2));
        }

        for (SectionReview review : snapshot.reviews) {
            MemberRawScore score = rawByUserId.computeIfAbsent(review.getReviewerId(), ignored -> new MemberRawScore(null));
            score.reviewRaw = score.reviewRaw.add("APPROVED".equalsIgnoreCase(review.getReviewResult())
                    ? BigDecimal.valueOf(2)
                    : BigDecimal.ONE);
        }

        for (SectionComment comment : snapshot.comments) {
            MemberRawScore score = rawByUserId.computeIfAbsent(comment.getUserId(), ignored -> new MemberRawScore(null));
            score.reviewRaw = score.reviewRaw.add(comment.getResolvedFlag() != null && comment.getResolvedFlag() == 1
                    ? BigDecimal.valueOf(1.5)
                    : BigDecimal.ONE);
        }

        BigDecimal totalTaskRaw = sum(rawByUserId.values(), member -> member.taskRaw);
        BigDecimal totalDocumentRaw = sum(rawByUserId.values(), member -> member.documentRaw);
        BigDecimal totalReportRaw = sum(rawByUserId.values(), member -> member.reportRaw);
        BigDecimal totalReviewRaw = sum(rawByUserId.values(), member -> member.reviewRaw);
        BigDecimal totalDefectRaw = sum(rawByUserId.values(), member -> member.defectRaw);

        List<ContributionVO.MemberContribution> members = rawByUserId.values().stream()
                .map(raw -> toContribution(raw, totalTaskRaw, totalDocumentRaw, totalReportRaw, totalReviewRaw, totalDefectRaw))
                .filter(member -> member.getUserId() != null)
                .sorted(Comparator.comparing(ContributionVO.MemberContribution::getTotalScore,
                        Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .toList();

        ContributionVO vo = new ContributionVO();
        vo.setProjectId(project.getId());
        vo.setMembers(members);
        return vo;
    }

    public RiskTrendVO riskTrend(Long projectId) {
        Project project = projectService.getProject(projectId);
        return riskTrend(project);
    }

    public RiskTrendVO riskTrend(Project project) {
        List<RiskSnapshot> snapshots = riskSnapshotMapper.selectList(new LambdaQueryWrapper<RiskSnapshot>()
                .eq(RiskSnapshot::getProjectId, project.getId())
                .orderByAsc(RiskSnapshot::getSnapshotTime)
                .orderByAsc(RiskSnapshot::getId));

        List<RiskTrendVO.RiskSnapshotItem> items;
        if (snapshots.isEmpty()) {
            items = List.of(buildInstantSnapshot(loadProjectStats(project)));
        } else {
            items = new ArrayList<>();
            RiskSnapshot previous = null;
            for (RiskSnapshot snapshot : snapshots) {
                RiskTrendVO.RiskSnapshotItem item = new RiskTrendVO.RiskSnapshotItem();
                item.setId(snapshot.getId());
                item.setRiskLevel(snapshot.getRiskLevel());
                item.setRiskScore(scale(snapshot.getRiskScore()));
                item.setTaskDelayCount(defaultInt(snapshot.getTaskDelayCount()));
                item.setBlockedTaskCount(defaultInt(snapshot.getBlockedTaskCount()));
                item.setMissingReportCount(defaultInt(snapshot.getMissingReportCount()));
                item.setRejectedSectionCount(defaultInt(snapshot.getRejectedSectionCount()));
                item.setTrend(StringUtils.hasText(snapshot.getTrend()) ? snapshot.getTrend() : deriveTrend(previous, snapshot));
                item.setSnapshotTime(snapshot.getSnapshotTime() != null ? snapshot.getSnapshotTime() : snapshot.getCreateTime());
                items.add(item);
                previous = snapshot;
            }
        }

        RiskTrendVO vo = new RiskTrendVO();
        vo.setProjectId(project.getId());
        vo.setSnapshots(items);
        return vo;
    }

    private ContributionVO.MemberContribution toContribution(MemberRawScore raw,
                                                             BigDecimal totalTaskRaw,
                                                             BigDecimal totalDocumentRaw,
                                                             BigDecimal totalReportRaw,
                                                             BigDecimal totalReviewRaw,
                                                             BigDecimal totalDefectRaw) {
        SysUser user = raw.userId == null ? null : sysUserMapper.selectById(raw.userId);

        ContributionVO.MemberContribution member = new ContributionVO.MemberContribution();
        member.setMemberId(raw.memberId);
        member.setUserId(raw.userId);
        member.setRealName(user == null ? "" : user.getRealName());
        member.setRoleName(raw.roleName);
        member.setTaskScore(share(raw.taskRaw, totalTaskRaw, BigDecimal.valueOf(40)));
        member.setDocumentScore(share(raw.documentRaw, totalDocumentRaw, BigDecimal.valueOf(20)));
        member.setReportScore(share(raw.reportRaw, totalReportRaw, BigDecimal.valueOf(15)));
        member.setReviewScore(share(raw.reviewRaw, totalReviewRaw, BigDecimal.valueOf(15)));
        member.setDefectScore(share(raw.defectRaw, totalDefectRaw, BigDecimal.valueOf(10)));
        member.setTotalScore(member.getTaskScore()
                .add(member.getDocumentScore())
                .add(member.getReportScore())
                .add(member.getReviewScore())
                .add(member.getDefectScore())
                .setScale(2, RoundingMode.HALF_UP));
        return member;
    }

    private ProjectStatsSnapshot loadProjectStats(Project project) {
        ProjectStatsSnapshot snapshot = new ProjectStatsSnapshot();
        snapshot.project = project;
        snapshot.sections = projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, project.getId())
                .orderByAsc(ProjectSection::getSortOrder)
                .orderByAsc(ProjectSection::getId));
        snapshot.sectionById = snapshot.sections.stream()
                .collect(Collectors.toMap(ProjectSection::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        snapshot.contents = sectionContentMapper.selectList(new LambdaQueryWrapper<SectionContent>()
                .eq(SectionContent::getProjectId, project.getId())
                .orderByAsc(SectionContent::getSectionId)
                .orderByDesc(SectionContent::getVersionNo)
                .orderByDesc(SectionContent::getId));
        snapshot.latestContentBySectionId = snapshot.contents.stream()
                .collect(Collectors.toMap(SectionContent::getSectionId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Set<Long> sectionIds = snapshot.sections.stream().map(ProjectSection::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        snapshot.reviews = sectionIds.isEmpty() ? List.of() : sectionReviewMapper.selectList(new LambdaQueryWrapper<SectionReview>()
                .in(SectionReview::getSectionId, sectionIds)
                .orderByDesc(SectionReview::getCreateTime)
                .orderByDesc(SectionReview::getId));
        snapshot.comments = sectionIds.isEmpty() ? List.of() : sectionCommentMapper.selectList(new LambdaQueryWrapper<SectionComment>()
                .in(SectionComment::getSectionId, sectionIds)
                .orderByDesc(SectionComment::getCreateTime)
                .orderByDesc(SectionComment::getId));
        snapshot.tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, project.getId())
                .orderByDesc(Task::getCreateTime)
                .orderByDesc(Task::getId));
        snapshot.progressRecords = progressRecordMapper.selectList(new LambdaQueryWrapper<ProgressRecord>()
                .eq(ProgressRecord::getProjectId, project.getId())
                .orderByDesc(ProgressRecord::getSubmitTime)
                .orderByDesc(ProgressRecord::getId));
        snapshot.members = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId())
                .orderByAsc(ProjectMember::getId));
        snapshot.aiRecords = aiRecordMapper.selectList(new LambdaQueryWrapper<AiRecord>()
                .eq(AiRecord::getProjectId, project.getId())
                .orderByDesc(AiRecord::getCreateTime)
                .orderByDesc(AiRecord::getId));
        snapshot.exportCount = exportRecordMapper.selectCount(new LambdaQueryWrapper<com.example.teamflow.entity.ExportRecord>()
                .eq(com.example.teamflow.entity.ExportRecord::getProjectId, project.getId())
                .eq(com.example.teamflow.entity.ExportRecord::getStatus, "GENERATED"));
        return snapshot;
    }

    private BigDecimal scoreRequiredSections(ProjectStatsSnapshot snapshot, List<String> deductionReasons) {
        List<ProjectSection> requiredSections = snapshot.sections.stream()
                .filter(section -> section.getRequiredFlag() != null && section.getRequiredFlag() == 1)
                .toList();
        if (requiredSections.isEmpty()) {
            return REQUIRED_SECTION_WEIGHT;
        }
        long approvedCount = requiredSections.stream()
                .filter(section -> "APPROVED".equalsIgnoreCase(section.getStatus()))
                .count();
        if (approvedCount < requiredSections.size()) {
            deductionReasons.add("必填章节完成不足：" + approvedCount + "/" + requiredSections.size());
        }
        return ratioScore(approvedCount, requiredSections.size(), REQUIRED_SECTION_WEIGHT);
    }

    private BigDecimal scoreReviewPass(ProjectStatsSnapshot snapshot, List<String> deductionReasons) {
        if (snapshot.reviews.isEmpty()) {
            long reviewingCount = snapshot.sections.stream()
                    .filter(section -> "REVIEWING".equalsIgnoreCase(section.getStatus()))
                    .count();
            if (reviewingCount > 0) {
                deductionReasons.add("仍有章节处于待审核状态：" + reviewingCount + " 个");
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return REVIEW_PASS_WEIGHT;
        }
        long approvedReviews = snapshot.reviews.stream()
                .filter(review -> "APPROVED".equalsIgnoreCase(review.getReviewResult()))
                .count();
        long rejectedReviews = snapshot.reviews.size() - approvedReviews;
        if (rejectedReviews > 0) {
            deductionReasons.add("章节审核通过率不足，存在 " + rejectedReviews + " 次驳回");
        }
        return ratioScore(approvedReviews, snapshot.reviews.size(), REVIEW_PASS_WEIGHT);
    }

    private BigDecimal scoreTaskCompletion(ProjectStatsSnapshot snapshot, List<String> deductionReasons) {
        if (snapshot.tasks.isEmpty()) {
            return TASK_COMPLETION_WEIGHT;
        }
        long doneCount = snapshot.tasks.stream().filter(task -> "DONE".equalsIgnoreCase(task.getStatus())).count();
        long overdueCount = snapshot.tasks.stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now()))
                .filter(task -> !"DONE".equalsIgnoreCase(task.getStatus()))
                .count();
        if (doneCount < snapshot.tasks.size()) {
            deductionReasons.add("任务完成率不足：" + doneCount + "/" + snapshot.tasks.size());
        }
        if (overdueCount > 0) {
            deductionReasons.add("存在逾期未完成任务：" + overdueCount + " 个");
        }
        return ratioScore(doneCount, snapshot.tasks.size(), TASK_COMPLETION_WEIGHT);
    }

    private BigDecimal scoreDefectClosure(ProjectStatsSnapshot snapshot, List<String> deductionReasons) {
        List<Task> issueTasks = snapshot.tasks.stream()
                .filter(task -> "BLOCKED".equalsIgnoreCase(task.getStatus())
                        || StringUtils.hasText(task.getBlockReason())
                        || "BUG".equalsIgnoreCase(task.getPriority())
                        || "DEFECT".equalsIgnoreCase(task.getPriority()))
                .toList();
        if (issueTasks.isEmpty()) {
            return DEFECT_CLOSURE_WEIGHT;
        }
        long closedCount = issueTasks.stream().filter(task -> "DONE".equalsIgnoreCase(task.getStatus())).count();
        long openCount = issueTasks.size() - closedCount;
        if (openCount > 0) {
            deductionReasons.add("阻塞/缺陷任务仍未闭环：" + openCount + " 个");
        }
        return ratioScore(closedCount, issueTasks.size(), DEFECT_CLOSURE_WEIGHT);
    }

    private BigDecimal scoreExportReadiness(ProjectStatsSnapshot snapshot, List<String> deductionReasons) {
        boolean summaryReady = snapshot.aiRecords.stream().anyMatch(record -> "SUMMARY_REPORT".equalsIgnoreCase(record.getType()));
        int missingReportCount = estimateMissingReportCount(snapshot);
        boolean reportReady = missingReportCount == 0;
        boolean exportReady = snapshot.exportCount != null && snapshot.exportCount > 0;

        BigDecimal ratio = BigDecimal.ZERO;
        if (summaryReady) {
            ratio = ratio.add(BigDecimal.valueOf(0.4));
        } else {
            deductionReasons.add("缺少 AI 总结记录");
        }
        if (reportReady) {
            ratio = ratio.add(BigDecimal.valueOf(0.2));
        } else {
            deductionReasons.add("进度报告提交不完整，缺口约 " + missingReportCount + " 人次");
        }
        if (exportReady) {
            ratio = ratio.add(BigDecimal.valueOf(0.4));
        } else {
            deductionReasons.add("尚未生成正式导出成果");
        }
        return EXPORT_READINESS_WEIGHT.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private RiskTrendVO.RiskSnapshotItem buildInstantSnapshot(ProjectStatsSnapshot snapshot) {
        int delayedCount = (int) snapshot.tasks.stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now()))
                .filter(task -> !"DONE".equalsIgnoreCase(task.getStatus()))
                .count();
        int blockedCount = (int) snapshot.tasks.stream()
                .filter(task -> "BLOCKED".equalsIgnoreCase(task.getStatus()))
                .count();
        int missingReportCount = estimateMissingReportCount(snapshot);
        int rejectedSectionCount = (int) snapshot.sections.stream()
                .filter(section -> "REJECTED".equalsIgnoreCase(section.getStatus()))
                .count();

        BigDecimal score = BigDecimal.valueOf(delayedCount * 10L
                        + blockedCount * 15L
                        + missingReportCount * 12L
                        + rejectedSectionCount * 18L)
                .min(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        RiskTrendVO.RiskSnapshotItem item = new RiskTrendVO.RiskSnapshotItem();
        item.setId(null);
        item.setRiskLevel(score.compareTo(BigDecimal.valueOf(70)) >= 0 ? "HIGH"
                : score.compareTo(BigDecimal.valueOf(35)) >= 0 ? "MEDIUM" : "LOW");
        item.setRiskScore(score);
        item.setTaskDelayCount(delayedCount);
        item.setBlockedTaskCount(blockedCount);
        item.setMissingReportCount(missingReportCount);
        item.setRejectedSectionCount(rejectedSectionCount);
        item.setTrend("CURRENT");
        item.setSnapshotTime(LocalDateTime.now());
        return item;
    }

    private int estimateMissingReportCount(ProjectStatsSnapshot snapshot) {
        Set<Long> memberUserIds = snapshot.members.stream()
                .map(ProjectMember::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (snapshot.project.getCreatorId() != null) {
            memberUserIds.add(snapshot.project.getCreatorId());
        }
        if (memberUserIds.isEmpty()) {
            return 0;
        }
        Set<Long> submittedUserIds = snapshot.progressRecords.stream()
                .filter(record -> currentPeriod().equals(normalize(record.getReportPeriod())))
                .filter(record -> Set.of("NORMAL", "LATE", "SUPPLEMENTED")
                        .contains(normalize(record.getSubmitStatus())))
                .map(ProgressRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int missing = 0;
        for (Long userId : memberUserIds) {
            if (!submittedUserIds.contains(userId)) {
                missing++;
            }
        }
        long explicitMissing = snapshot.progressRecords.stream()
                .filter(record -> currentPeriod().equals(normalize(record.getReportPeriod())))
                .filter(record -> "MISSING".equalsIgnoreCase(record.getSubmitStatus()))
                .count();
        return Math.max(missing, (int) explicitMissing);
    }

    private String currentPeriod() {
        LocalDate date = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        return String.format("%04d-W%02d", date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String deriveTrend(RiskSnapshot previous, RiskSnapshot current) {
        if (previous == null || previous.getRiskScore() == null || current.getRiskScore() == null) {
            return "STABLE";
        }
        int compare = current.getRiskScore().compareTo(previous.getRiskScore());
        if (compare > 0) {
            return "UP";
        }
        if (compare < 0) {
            return "DOWN";
        }
        return "STABLE";
    }

    private BigDecimal ratioScore(long numerator, long denominator, BigDecimal weight) {
        if (denominator <= 0) {
            return weight.setScale(2, RoundingMode.HALF_UP);
        }
        return weight.multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal share(BigDecimal value, BigDecimal total, BigDecimal weight) {
        if (value == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return weight.multiply(value).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(Collection<MemberRawScore> raws, Function<MemberRawScore, BigDecimal> extractor) {
        return raws.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static class ProjectStatsSnapshot {
        private Project project;
        private List<ProjectSection> sections = List.of();
        private Map<Long, ProjectSection> sectionById = Map.of();
        private List<SectionContent> contents = List.of();
        private Map<Long, SectionContent> latestContentBySectionId = Map.of();
        private List<SectionReview> reviews = List.of();
        private List<SectionComment> comments = List.of();
        private List<Task> tasks = List.of();
        private List<ProgressRecord> progressRecords = List.of();
        private List<ProjectMember> members = List.of();
        private List<AiRecord> aiRecords = List.of();
        private Long exportCount = 0L;
    }

    private static class MemberRawScore {
        private final Long memberId;
        private final Long userId;
        private String roleName;
        private BigDecimal taskRaw = BigDecimal.ZERO;
        private BigDecimal documentRaw = BigDecimal.ZERO;
        private BigDecimal reportRaw = BigDecimal.ZERO;
        private BigDecimal reviewRaw = BigDecimal.ZERO;
        private BigDecimal defectRaw = BigDecimal.ZERO;

        private MemberRawScore(ProjectMember member) {
            this.memberId = member == null ? null : member.getId();
            this.userId = member == null ? null : member.getUserId();
            this.roleName = member == null ? null : (StringUtils.hasText(member.getMemberTitle()) ? member.getMemberTitle() : member.getMemberRole());
        }
    }
}
