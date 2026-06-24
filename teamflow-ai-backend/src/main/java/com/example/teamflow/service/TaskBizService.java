package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.TaskDTO;
import com.example.teamflow.dto.TaskStatusDTO;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.entity.TaskLog;
import com.example.teamflow.mapper.ProjectMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskLogMapper;
import com.example.teamflow.mapper.TaskMapper;
import com.example.teamflow.vo.TaskLogVO;
import com.example.teamflow.vo.TaskVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskBizService {
    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "REVIEW", "DONE", "BLOCKED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final String PROJECT_MANAGER = "PROJECT_MANAGER";

    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final ProjectMapper projectMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final AuthService authService;
    private final ProjectService projectService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TaskVO create(TaskDTO dto) {
        Project project = projectService.getProject(dto.getProjectId());
        projectService.ensureManager(project);
        SysUser current = authService.currentUser();
        ProjectSection section = validateSection(project.getId(), dto.getSectionId());
        validateAssignee(project.getId(), dto.getAssigneeId());

        Task task = new Task();
        copyBase(dto, task);
        task.setSectionId(section == null ? null : section.getId());
        task.setCreatorId(current.getId());
        task.setStatus(normalizeStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "TODO"));
        task.setPriority(normalizePriority(StringUtils.hasText(dto.getPriority()) ? dto.getPriority() : "MEDIUM"));
        applyStatusSideEffects(task, null, task.getStatus(), dto.getBlockReason());
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskMapper.insert(task);

        writeLog(task.getId(), current.getId(), "CREATE", null, task.getStatus(), null, task.getTitle(),
                "创建任务：" + task.getTitle());
        if (task.getAssigneeId() != null) {
            writeLog(task.getId(), current.getId(), "ASSIGN", null, null, null, String.valueOf(task.getAssigneeId()),
                    "任务分配给：" + userName(task.getAssigneeId()));
        }
        if ("URGENT".equals(task.getPriority())) {
            writeLog(task.getId(), current.getId(), "PRIORITY", null, null, null, "URGENT",
                    "任务优先级设为紧急");
        }
        if ("BLOCKED".equals(task.getStatus())) {
            writeLog(task.getId(), current.getId(), "BLOCKED", null, "BLOCKED", null, task.getBlockReason(),
                    "任务被阻塞：" + task.getBlockReason());
        }
        notifyTaskEvents(project, task, null, null, null, current);
        return toVO(task);
    }

    @Transactional
    public TaskVO update(Long id, TaskDTO dto) {
        Task task = getTask(id);
        Project project = projectService.getProject(task.getProjectId());
        projectService.ensureManager(project);
        if (dto.getProjectId() != null && !Objects.equals(dto.getProjectId(), task.getProjectId())) {
            throw new BizException(400, "不能变更任务所属项目");
        }
        SysUser current = authService.currentUser();
        validateSection(task.getProjectId(), dto.getSectionId());
        validateAssignee(task.getProjectId(), dto.getAssigneeId());

        String oldStatus = task.getStatus();
        String oldPriority = task.getPriority();
        Long oldAssigneeId = task.getAssigneeId();
        String oldBlockReason = task.getBlockReason();
        Long oldSectionId = task.getSectionId();
        Long oldProjectId = task.getProjectId();
        String oldTitle = task.getTitle();
        String oldDescription = task.getDescription();
        LocalDate oldStartDate = task.getStartDate();
        LocalDate oldDueDate = task.getDueDate();

        copyBase(dto, task);
        task.setProjectId(oldProjectId);
        task.setSectionId(dto.getSectionId());
        if (StringUtils.hasText(dto.getStatus())) {
            task.setStatus(normalizeStatus(dto.getStatus()));
        }
        if (StringUtils.hasText(dto.getPriority())) {
            task.setPriority(normalizePriority(dto.getPriority()));
        }
        applyStatusSideEffects(task, oldStatus, task.getStatus(), dto.getBlockReason());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        if (!Objects.equals(oldAssigneeId, task.getAssigneeId())) {
            writeLog(task.getId(), current.getId(), "ASSIGN", null, null, stringValue(oldAssigneeId),
                    stringValue(task.getAssigneeId()), "任务负责人由 " + userName(oldAssigneeId) + " 调整为 " + userName(task.getAssigneeId()));
        }
        if (!Objects.equals(oldPriority, task.getPriority())) {
            writeLog(task.getId(), current.getId(), "PRIORITY", null, null, oldPriority, task.getPriority(),
                    "任务优先级由 " + oldPriority + " 调整为 " + task.getPriority());
        }
        if (!Objects.equals(oldStatus, task.getStatus())) {
            writeLog(task.getId(), current.getId(), "STATUS", oldStatus, task.getStatus(), null, null,
                    "任务状态由 " + oldStatus + " 修改为 " + task.getStatus());
        }
        if (!Objects.equals(oldBlockReason, task.getBlockReason()) || becameBlocked(oldStatus, task.getStatus())) {
            writeLog(task.getId(), current.getId(), "BLOCKED", oldStatus, task.getStatus(), oldBlockReason,
                    task.getBlockReason(), "任务阻塞状态更新：" + (task.getBlockReason() == null ? "已解除" : task.getBlockReason()));
        }
        if (baseChanged(task, oldSectionId, oldTitle, oldDescription, oldStartDate, oldDueDate)) {
            writeLog(task.getId(), current.getId(), "UPDATE", oldStatus, task.getStatus(), null, null,
                    "更新任务基础信息：" + task.getTitle());
        }
        notifyTaskEvents(project, task, oldAssigneeId, oldPriority, oldStatus, current);
        return toVO(task);
    }

    @Transactional
    public TaskVO updateStatus(Long id, TaskStatusDTO dto) {
        Task task = getTask(id);
        Project project = projectService.getProject(task.getProjectId());
        SysUser current = authService.currentUser();
        if (!Objects.equals(task.getAssigneeId(), current.getId())) {
            projectService.ensureManager(project);
        }

        String oldStatus = task.getStatus();
        String oldBlockReason = task.getBlockReason();
        task.setStatus(normalizeStatus(dto.getStatus()));
        applyStatusSideEffects(task, oldStatus, task.getStatus(), dto.getBlockReason());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        String content = StringUtils.hasText(dto.getContent())
                ? dto.getContent()
                : "任务状态由 " + oldStatus + " 修改为 " + task.getStatus();
        writeLog(task.getId(), current.getId(), "STATUS", oldStatus, task.getStatus(), null, null, content);
        if (!Objects.equals(oldBlockReason, task.getBlockReason()) || becameBlocked(oldStatus, task.getStatus())) {
            writeLog(task.getId(), current.getId(), "BLOCKED", oldStatus, task.getStatus(), oldBlockReason,
                    task.getBlockReason(), "任务阻塞状态更新：" + (task.getBlockReason() == null ? "已解除" : task.getBlockReason()));
        }
        notifyTaskEvents(project, task, task.getAssigneeId(), task.getPriority(), oldStatus, current);
        return toVO(task);
    }

    public List<TaskVO> listByProject(Long projectId) {
        projectService.getProject(projectId);
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .orderByAsc(Task::getDueDate)
                        .orderByDesc(Task::getCreateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public List<TaskVO> myTasks() {
        SysUser current = authService.currentUser();
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getAssigneeId, current.getId())
                        .orderByAsc(Task::getDueDate)
                        .orderByDesc(Task::getCreateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public List<TaskLogVO> logs(Long taskId) {
        Task task = getTask(taskId);
        projectService.getProject(task.getProjectId());
        return taskLogMapper.selectList(new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getTaskId, taskId)
                        .orderByDesc(TaskLog::getCreateTime)
                        .orderByDesc(TaskLog::getId))
                .stream()
                .map(this::toLogVO)
                .toList();
    }

    private Task getTask(Long id) {
        Task task = id == null ? null : taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        return task;
    }

    private void copyBase(TaskDTO dto, Task task) {
        task.setProjectId(dto.getProjectId());
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setAssigneeId(dto.getAssigneeId());
        task.setStartDate(dto.getStartDate());
        task.setDueDate(dto.getDueDate());
    }

    private ProjectSection validateSection(Long projectId, Long sectionId) {
        if (sectionId == null) {
            return null;
        }
        ProjectSection section = projectSectionMapper.selectById(sectionId);
        if (section == null) {
            throw new BizException(404, "章节不存在");
        }
        if (!Objects.equals(projectId, section.getProjectId())) {
            throw new BizException(400, "任务章节不属于当前项目");
        }
        return section;
    }

    private void validateAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(assigneeId);
        if (user == null) {
            throw new BizException(404, "任务负责人不存在");
        }
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, assigneeId)
                .last("limit 1"));
        Project project = projectMapper.selectById(projectId);
        if (member == null && (project == null || !Objects.equals(project.getCreatorId(), assigneeId))) {
            throw new BizException(400, "任务负责人不是项目成员");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = normalize(status);
        if (!STATUSES.contains(normalized)) {
            throw new BizException(400, "任务状态无效");
        }
        return normalized;
    }

    private String normalizePriority(String priority) {
        String normalized = normalize(priority);
        if (!PRIORITIES.contains(normalized)) {
            throw new BizException(400, "任务优先级无效");
        }
        return normalized;
    }

    private void applyStatusSideEffects(Task task, String oldStatus, String newStatus, String blockReason) {
        if ("BLOCKED".equals(newStatus)) {
            if (StringUtils.hasText(blockReason)) {
                task.setBlockReason(blockReason);
            }
            if (!StringUtils.hasText(task.getBlockReason())) {
                throw new BizException(400, "阻塞任务必须填写阻塞原因");
            }
        } else if ("BLOCKED".equals(oldStatus)) {
            task.setBlockReason(StringUtils.hasText(blockReason) ? blockReason : null);
        } else if (StringUtils.hasText(blockReason)) {
            task.setBlockReason(blockReason);
        }

        if ("DONE".equals(newStatus)) {
            if (!"DONE".equals(oldStatus) || task.getFinishTime() == null) {
                task.setFinishTime(LocalDateTime.now());
            }
        } else if ("DONE".equals(oldStatus)) {
            task.setFinishTime(null);
        }
    }

    private boolean becameBlocked(String oldStatus, String newStatus) {
        return !"BLOCKED".equals(oldStatus) && "BLOCKED".equals(newStatus);
    }

    private boolean baseChanged(Task task, Long oldSectionId, String oldTitle, String oldDescription,
                                LocalDate oldStartDate, LocalDate oldDueDate) {
        return !Objects.equals(oldSectionId, task.getSectionId())
                || !Objects.equals(oldTitle, task.getTitle())
                || !Objects.equals(oldDescription, task.getDescription())
                || !Objects.equals(oldStartDate, task.getStartDate())
                || !Objects.equals(oldDueDate, task.getDueDate());
    }

    private void writeLog(Long taskId, Long userId, String actionType, String oldStatus, String newStatus,
                          String oldValue, String newValue, String content) {
        TaskLog log = new TaskLog();
        log.setTaskId(taskId);
        log.setUserId(userId);
        log.setActionType(actionType);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setContent(content);
        log.setCreateTime(LocalDateTime.now());
        taskLogMapper.insert(log);
    }

    private void notifyTaskEvents(Project project, Task task, Long oldAssigneeId, String oldPriority,
                                  String oldStatus, SysUser actor) {
        if (task.getAssigneeId() != null && !Objects.equals(oldAssigneeId, task.getAssigneeId())) {
            notifyOne(task.getAssigneeId(), project.getId(), "你有新的任务",
                    actor.getRealName() + " 将任务分配给你：" + task.getTitle(), "TASK_ASSIGNMENT", taskLink(task));
        }
        if ("URGENT".equals(task.getPriority()) && !"URGENT".equals(oldPriority)) {
            notifyParticipants(project, task, "紧急任务提醒", "任务被标记为紧急：" + task.getTitle(), "TASK_ASSIGNMENT");
        }
        if ("BLOCKED".equals(task.getStatus()) && !"BLOCKED".equals(oldStatus)) {
            notifyManagers(project, "任务阻塞提醒", "任务被阻塞：" + task.getTitle() + "，原因：" + task.getBlockReason(), "TASK_BLOCKED", taskLink(task));
        }
        if (("REVIEW".equals(task.getStatus()) || "DONE".equals(task.getStatus())) && !Objects.equals(oldStatus, task.getStatus())) {
            notifyParticipants(project, task, "任务状态更新", "任务「" + task.getTitle() + "」已变更为 " + task.getStatus(), "TASK_ASSIGNMENT");
        }
    }

    private void notifyParticipants(Project project, Task task, String title, String content, String type) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (task.getAssigneeId() != null) {
            userIds.add(task.getAssigneeId());
        }
        if (task.getCreatorId() != null) {
            userIds.add(task.getCreatorId());
        }
        if (project.getCreatorId() != null) {
            userIds.add(project.getCreatorId());
        }
        managerIds(project.getId()).forEach(userIds::add);
        userIds.forEach(userId -> notifyOne(userId, project.getId(), title, content, type, taskLink(task)));
    }

    private void notifyManagers(Project project, String title, String content, String type, String link) {
        Set<Long> userIds = new LinkedHashSet<>(managerIds(project.getId()));
        if (project.getCreatorId() != null) {
            userIds.add(project.getCreatorId());
        }
        userIds.forEach(userId -> notifyOne(userId, project.getId(), title, content, type, link));
    }

    private List<Long> managerIds(Long projectId) {
        return projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getMemberRole, PROJECT_MANAGER))
                .stream()
                .map(ProjectMember::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void notifyOne(Long userId, Long projectId, String title, String content, String type, String link) {
        if (userId != null) {
            notificationService.notifyUser(userId, projectId, title, content, type, link);
        }
    }

    private TaskVO toVO(Task task) {
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setProjectId(task.getProjectId());
        Project project = projectMapper.selectById(task.getProjectId());
        vo.setProjectName(project == null ? "" : project.getProjectName());
        vo.setSectionId(task.getSectionId());
        ProjectSection section = task.getSectionId() == null ? null : projectSectionMapper.selectById(task.getSectionId());
        vo.setSectionName(section == null ? "" : section.getSectionName());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setAssigneeId(task.getAssigneeId());
        vo.setAssigneeName(userName(task.getAssigneeId()));
        vo.setCreatorId(task.getCreatorId());
        vo.setCreatorName(userName(task.getCreatorId()));
        vo.setStatus(task.getStatus());
        vo.setPriority(task.getPriority());
        vo.setStartDate(task.getStartDate());
        vo.setDueDate(task.getDueDate());
        vo.setFinishTime(task.getFinishTime());
        vo.setBlockReason(task.getBlockReason());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private TaskLogVO toLogVO(TaskLog log) {
        TaskLogVO vo = new TaskLogVO();
        vo.setId(log.getId());
        vo.setTaskId(log.getTaskId());
        vo.setUserId(log.getUserId());
        vo.setUsername(userName(log.getUserId()));
        vo.setActionType(log.getActionType());
        vo.setOldStatus(log.getOldStatus());
        vo.setNewStatus(log.getNewStatus());
        vo.setOldValue(log.getOldValue());
        vo.setNewValue(log.getNewValue());
        vo.setContent(log.getContent());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private String userName(Long userId) {
        if (userId == null) {
            return "未分配";
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? "未知用户" : user.getRealName();
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String taskLink(Task task) {
        return "/projects/" + task.getProjectId() + "/tasks/" + task.getId();
    }

    @Transactional
    public void syncTasksFromWbs(Long projectId) {
        Project project = projectService.getProject(projectId);
        projectService.ensureManager(project);

        String wbsData = project.getWbsData();
        if (!StringUtils.hasText(wbsData)) {
            throw new BizException(400, "WBS计划尚未保存，无法同步");
        }

        try {
            JsonNode root = objectMapper.readTree(wbsData);
            JsonNode wbsList = root.get("wbs");
            if (wbsList == null || !wbsList.isArray()) {
                return;
            }

            SysUser current = authService.currentUser();
            
            List<Task> existingTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .eq(Task::getProjectId, projectId));

            for (JsonNode taskNode : wbsList) {
                boolean isParent = taskNode.path("isParent").asBoolean(false);
                if (isParent) {
                    continue;
                }

                String title = taskNode.path("title").asText().trim();
                if (!StringUtils.hasText(title)) {
                    continue;
                }

                int progress = taskNode.path("progress").asInt(0);
                String status = "TODO";
                if (progress >= 100) {
                    status = "DONE";
                } else if (progress > 0) {
                    status = "IN_PROGRESS";
                }

                String startDateStr = taskNode.path("startDate").asText();
                String endDateStr = taskNode.path("endDate").asText();
                LocalDate startDate = StringUtils.hasText(startDateStr) ? LocalDate.parse(startDateStr) : null;
                LocalDate dueDate = StringUtils.hasText(endDateStr) ? LocalDate.parse(endDateStr) : null;

                Long assigneeId = null;
                JsonNode resourceIds = taskNode.path("resourceIds");
                if (resourceIds != null && resourceIds.isArray() && resourceIds.size() > 0) {
                    for (JsonNode resNode : resourceIds) {
                        String resIdStr = resNode.asText();
                        try {
                            long potentialUserId = Long.parseLong(resIdStr);
                            ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                                    .eq(ProjectMember::getProjectId, projectId)
                                    .eq(ProjectMember::getUserId, potentialUserId)
                                    .last("limit 1"));
                            if (member != null) {
                                assigneeId = potentialUserId;
                                break;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                Task existing = existingTasks.stream()
                        .filter(t -> t.getTitle().trim().equalsIgnoreCase(title))
                        .findFirst()
                        .orElse(null);

                LocalDateTime now = LocalDateTime.now();
                if (existing != null) {
                    existing.setStartDate(startDate);
                    existing.setDueDate(dueDate);
                    existing.setAssigneeId(assigneeId);
                    existing.setStatus(status);
                    existing.setUpdateTime(now);
                    taskMapper.updateById(existing);
                    
                    writeLog(existing.getId(), current.getId(), "UPDATE", null, status, null, title,
                            "从 WBS 计划同步更新任务");
                } else {
                    Task task = new Task();
                    task.setProjectId(projectId);
                    task.setTitle(title);
                    task.setDescription("根据 WBS 计划 “" + title + "” 自动生成");
                    task.setAssigneeId(assigneeId);
                    task.setCreatorId(current.getId());
                    task.setStatus(status);
                    task.setPriority("MEDIUM");
                    task.setStartDate(startDate);
                    task.setDueDate(dueDate);
                    task.setCreateTime(now);
                    task.setUpdateTime(now);
                    taskMapper.insert(task);

                    writeLog(task.getId(), current.getId(), "CREATE", null, status, null, title,
                            "从 WBS 计划自动生成任务");
                }
            }
        } catch (Exception e) {
            throw new BizException(500, "同步任务失败: " + e.getMessage());
        }
    }
}
