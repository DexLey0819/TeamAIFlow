package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.ProgressReportDTO;
import com.example.teamflow.entity.ProgressRecord;
import com.example.teamflow.entity.ProgressReportRule;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.ProgressRecordMapper;
import com.example.teamflow.mapper.ProgressReportRuleMapper;
import com.example.teamflow.mapper.ProjectMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskMapper;
import com.example.teamflow.vo.ProgressNeedSubmitVO;
import com.example.teamflow.vo.ProgressStatusVO;
import com.example.teamflow.vo.ReportRuleVO;
import com.example.teamflow.vo.ProgressRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProgressService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^(\\d{4})-W(\\d{1,2})$");
    private static final String PROJECT_MANAGER = "PROJECT_MANAGER";

    private final ProgressRecordMapper progressRecordMapper;
    private final ProgressReportRuleMapper progressReportRuleMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final TaskMapper taskMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectService projectService;
    private final AuthService authService;
    private final NotificationService notificationService;

    public List<ProgressNeedSubmitVO> needSubmit() {
        SysUser current = authService.currentUser();
        String period = currentPeriod();
        LocalDate weekStart = weekStartForPeriod(period);
        LocalDateTime now = LocalDateTime.now();

        return reportingProjects(current).stream()
                .map(project -> toNeedSubmitVO(project, current.getId(), period, weekStart, now))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProgressNeedSubmitVO::getOverdue).reversed()
                        .thenComparing(ProgressNeedSubmitVO::getProjectName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional
    public void submitReport(ProgressReportDTO dto) {
        Project project = projectService.getProject(dto.getProjectId());
        SysUser current = authService.currentUser();
        ProjectMember member = findMember(project.getId(), current.getId());
        if (member == null) {
            throw new BizException(403, "仅项目成员可提交进度报告");
        }
        ProgressReportRule rule = requiredRule(project.getId());
        if (rule == null) {
            throw new BizException(400, "当前项目未开启进度报告");
        }

        String period = StringUtils.hasText(dto.getReportPeriod()) ? normalizePeriod(dto.getReportPeriod()) : currentPeriod();
        LocalDate weekStart = weekStartForPeriod(period);
        LocalDate weekEnd = weekStart.plusDays(6);
        ProgressRecord existing = progressRecordMapper.selectOne(new LambdaQueryWrapper<ProgressRecord>()
                .eq(ProgressRecord::getProjectId, project.getId())
                .eq(ProgressRecord::getUserId, current.getId())
                .eq(ProgressRecord::getReportPeriod, period)
                .last("limit 1"));
        if (existing != null) {
            throw new BizException(400, "当前周期已提交进度报告");
        }

        validateRelatedTask(project.getId(), dto.getRelatedTaskId());
        validateRelatedSection(project.getId(), dto.getRelatedSectionId());

        LocalDateTime now = LocalDateTime.now();
        String submitStatus = submitStatus(rule, period, weekStart, now);
        ProgressRecord record = new ProgressRecord();
        record.setProjectId(project.getId());
        record.setUserId(current.getId());
        record.setProjectRoleId(member == null ? null : member.getProjectRoleId());
        record.setRelatedTaskId(dto.getRelatedTaskId());
        record.setRelatedSectionId(dto.getRelatedSectionId());
        record.setReportPeriod(period);
        record.setWeekStart(weekStart);
        record.setWeekEnd(weekEnd);
        record.setCompletedWork(dto.getCompletedWork());
        record.setProblems(dto.getProblems());
        record.setHelpNeeded(dto.getHelpNeeded());
        record.setNextPlan(dto.getNextPlan());
        record.setSubmitStatus(submitStatus);
        record.setSubmitTime(now);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        progressRecordMapper.insert(record);

        if ("LATE".equals(submitStatus) || "SUPPLEMENTED".equals(submitStatus)) {
            notifyManagers(project, "进度报告提交提醒",
                     current.getRealName() + " 提交了" + statusText(submitStatus) + "进度报告，周期：" + period,
                    "REPORT_OVERDUE", "/projects/" + project.getId() + "/progress");
        }
    }

    public ProgressStatusVO progressStatus(Long projectId) {
        Project project = projectService.getProject(projectId);
        requireProgressStatusAccess(project);
        ProgressReportRule rule = requiredRule(project.getId());
        String period = currentPeriod();

        ProgressStatusVO vo = new ProgressStatusVO();
        vo.setProjectId(project.getId());
        vo.setReportPeriod(period);
        vo.setReportRule(toRuleVO(rule));
        vo.setMemberStatuses(projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, project.getId())
                        .orderByAsc(ProjectMember::getId))
                .stream()
                .map(member -> toMemberStatus(member, period))
                .toList());
        return vo;
    }

    public List<ProgressRecordVO> progressRecords(Long projectId) {
        Project project = projectService.getProject(projectId);
        requireProgressStatusAccess(project);

        List<ProgressRecord> records = progressRecordMapper.selectList(new LambdaQueryWrapper<ProgressRecord>()
                .eq(ProgressRecord::getProjectId, projectId)
                .orderByDesc(ProgressRecord::getSubmitTime));

        return records.stream().map(record -> {
            ProgressRecordVO vo = new ProgressRecordVO();
            vo.setId(record.getId());
            vo.setProjectId(record.getProjectId());
            vo.setUserId(record.getUserId());
            vo.setRelatedTaskId(record.getRelatedTaskId());
            vo.setRelatedSectionId(record.getRelatedSectionId());
            vo.setReportPeriod(record.getReportPeriod());
            vo.setCompletedWork(record.getCompletedWork());
            vo.setProblems(record.getProblems());
            vo.setHelpNeeded(record.getHelpNeeded());
            vo.setNextPlan(record.getNextPlan());
            vo.setSubmitStatus(record.getSubmitStatus());
            vo.setSubmitTime(record.getSubmitTime());
            vo.setWeekStart(record.getWeekStart());
            vo.setWeekEnd(record.getWeekEnd());

            SysUser user = sysUserMapper.selectById(record.getUserId());
            vo.setRealName(user == null ? "" : user.getRealName());

            ProjectMember member = findMember(projectId, record.getUserId());
            vo.setRoleName(member == null ? "" : (StringUtils.hasText(member.getMemberTitle()) ? member.getMemberTitle() : member.getMemberRole()));

            if (record.getRelatedTaskId() != null) {
                Task task = taskMapper.selectById(record.getRelatedTaskId());
                if (task != null) {
                    vo.setRelatedTaskTitle(task.getTitle());
                }
            }

            return vo;
        }).toList();
    }

    private ProgressNeedSubmitVO toNeedSubmitVO(Project project, Long userId, String period, LocalDate weekStart, LocalDateTime now) {
        ProgressReportRule rule = requiredRule(project.getId());
        if (rule == null) {
            return null;
        }
        ProgressRecord record = progressRecordMapper.selectOne(new LambdaQueryWrapper<ProgressRecord>()
                .eq(ProgressRecord::getProjectId, project.getId())
                .eq(ProgressRecord::getUserId, userId)
                .eq(ProgressRecord::getReportPeriod, period)
                .last("limit 1"));
        ProgressNeedSubmitVO vo = new ProgressNeedSubmitVO();
        vo.setProjectId(project.getId());
        vo.setProjectName(project.getProjectName());
        vo.setNeedSubmit(record == null);
        vo.setOverdue(record == null && isOverdue(rule, weekStart, now));
        vo.setReportPeriod(period);
        vo.setReportRule(toRuleVO(rule));
        return vo;
    }

    private ProgressStatusVO.MemberStatus toMemberStatus(ProjectMember member, String period) {
        ProgressRecord record = progressRecordMapper.selectOne(new LambdaQueryWrapper<ProgressRecord>()
                .eq(ProgressRecord::getProjectId, member.getProjectId())
                .eq(ProgressRecord::getUserId, member.getUserId())
                .eq(ProgressRecord::getReportPeriod, period)
                .last("limit 1"));
        SysUser user = sysUserMapper.selectById(member.getUserId());

        ProgressStatusVO.MemberStatus status = new ProgressStatusVO.MemberStatus();
        status.setMemberId(member.getId());
        status.setUserId(member.getUserId());
        status.setRealName(user == null ? "" : user.getRealName());
        status.setRoleName(StringUtils.hasText(member.getMemberTitle()) ? member.getMemberTitle() : member.getMemberRole());
        status.setSubmitStatus(record == null ? "NOT_SUBMITTED" : record.getSubmitStatus());
        status.setSubmitTime(record == null ? null : record.getSubmitTime());
        return status;
    }

    private List<Project> reportingProjects(SysUser current) {
        Set<Long> ids = new LinkedHashSet<>();
        projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, current.getId()))
                .forEach(member -> ids.add(member.getProjectId()));
        if (ids.isEmpty()) {
            return List.of();
        }
        return projectMapper.selectBatchIds(ids).stream()
                .sorted(Comparator.comparing(Project::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private void requireProgressStatusAccess(Project project) {
        SysUser current = authService.currentUser();
        if (isAdmin(current)) {
            return;
        }
        projectService.ensureManager(project);
    }

    private ProgressReportRule requiredRule(Long projectId) {
        ProgressReportRule rule = progressReportRuleMapper.selectOne(new LambdaQueryWrapper<ProgressReportRule>()
                .eq(ProgressReportRule::getProjectId, projectId)
                .last("limit 1"));
        if (rule == null || Integer.valueOf(0).equals(rule.getRequiredFlag())) {
            return null;
        }
        return rule;
    }

    private ProjectMember findMember(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return null;
        }
        return projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
                .last("limit 1"));
    }

    private void validateRelatedTask(Long projectId, Long taskId) {
        if (taskId == null) {
            return;
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "关联任务不存在");
        }
        if (!Objects.equals(task.getProjectId(), projectId)) {
            throw new BizException(400, "关联任务不属于当前项目");
        }
    }

    private void validateRelatedSection(Long projectId, Long sectionId) {
        if (sectionId == null) {
            return;
        }
        ProjectSection section = projectSectionMapper.selectById(sectionId);
        if (section == null) {
            throw new BizException(404, "关联章节不存在");
        }
        if (!Objects.equals(section.getProjectId(), projectId)) {
            throw new BizException(400, "关联章节不属于当前项目");
        }
    }

    private String submitStatus(ProgressReportRule rule, String period, LocalDate weekStart, LocalDateTime now) {
        if (!currentPeriod().equals(period)) {
            return "SUPPLEMENTED";
        }
        return isOverdue(rule, weekStart, now) ? "LATE" : "NORMAL";
    }

    private boolean isOverdue(ProgressReportRule rule, LocalDate weekStart, LocalDateTime now) {
        return now.isAfter(dueAt(rule, weekStart));
    }

    private LocalDateTime dueAt(ProgressReportRule rule, LocalDate weekStart) {
        DayOfWeek day = parseReportDay(rule == null ? null : rule.getReportDay());
        LocalTime time = parseReportTime(rule == null ? null : rule.getReportTime());
        return weekStart.plusDays(day.getValue() - 1L).atTime(time);
    }

    private DayOfWeek parseReportDay(String value) {
        if (!StringUtils.hasText(value)) {
            return DayOfWeek.SUNDAY;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "MONDAY", "MON", "周一", "星期一" -> DayOfWeek.MONDAY;
            case "2", "TUESDAY", "TUE", "周二", "星期二" -> DayOfWeek.TUESDAY;
            case "3", "WEDNESDAY", "WED", "周三", "星期三" -> DayOfWeek.WEDNESDAY;
            case "4", "THURSDAY", "THU", "周四", "星期四" -> DayOfWeek.THURSDAY;
            case "5", "FRIDAY", "FRI", "周五", "星期五" -> DayOfWeek.FRIDAY;
            case "6", "SATURDAY", "SAT", "周六", "星期六" -> DayOfWeek.SATURDAY;
            case "7", "SUNDAY", "SUN", "周日", "周天", "星期日", "星期天" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private LocalTime parseReportTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalTime.of(23, 59);
        }
        try {
            return LocalTime.parse(value.trim().length() == 5 ? value.trim() + ":00" : value.trim());
        } catch (Exception ignored) {
            return LocalTime.of(23, 59);
        }
    }

    private String currentPeriod() {
        return formatPeriod(LocalDate.now());
    }

    private String normalizePeriod(String period) {
        Matcher matcher = PERIOD_PATTERN.matcher(period.trim().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new BizException(400, "进度周期格式应为 yyyy-Www");
        }
        int week = Integer.parseInt(matcher.group(2));
        if (week < 1 || week > 53) {
            throw new BizException(400, "进度周期周数无效");
        }
        return String.format("%s-W%02d", matcher.group(1), week);
    }

    private String formatPeriod(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        int year = date.get(weekFields.weekBasedYear());
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return String.format("%04d-W%02d", year, week);
    }

    private LocalDate weekStartForPeriod(String period) {
        Matcher matcher = PERIOD_PATTERN.matcher(normalizePeriod(period));
        if (!matcher.matches()) {
            throw new BizException(400, "进度周期格式应为 yyyy-Www");
        }
        int year = Integer.parseInt(matcher.group(1));
        int week = Integer.parseInt(matcher.group(2));
        WeekFields weekFields = WeekFields.ISO;
        try {
            return LocalDate.now()
                    .with(weekFields.weekBasedYear(), year)
                    .with(weekFields.weekOfWeekBasedYear(), week)
                    .with(DayOfWeek.MONDAY);
        } catch (Exception e) {
            throw new BizException(400, "进度周期无效");
        }
    }

    private ReportRuleVO toRuleVO(ProgressReportRule rule) {
        if (rule == null) {
            return null;
        }
        ReportRuleVO vo = new ReportRuleVO();
        vo.setId(rule.getId());
        vo.setProjectId(rule.getProjectId());
        vo.setFrequency(rule.getFrequency());
        vo.setReportDay(rule.getReportDay());
        vo.setReportTime(rule.getReportTime());
        vo.setRequiredFlag(rule.getRequiredFlag());
        vo.setOverduePolicy(rule.getOverduePolicy());
        return vo;
    }

    private void notifyManagers(Project project, String title, String content, String type, String link) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (project.getCreatorId() != null) {
            userIds.add(project.getCreatorId());
        }
        projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, project.getId())
                        .eq(ProjectMember::getMemberRole, PROJECT_MANAGER))
                .stream()
                .map(ProjectMember::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        userIds.forEach(userId -> notificationService.notifyUser(userId, project.getId(), title, content, type, link));
    }

    private String statusText(String submitStatus) {
        return "SUPPLEMENTED".equals(submitStatus) ? "补交" : "逾期";
    }

    private boolean isAdmin(SysUser user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
