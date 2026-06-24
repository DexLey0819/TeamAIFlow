package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.TaskMapper;
import com.example.teamflow.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ProjectMemberMapper projectMemberMapper;
    private final TaskMapper taskMapper;
    private final AuthService authService;
    private final TaskBizService taskBizService;
    private final StatisticsService statisticsService;
    private final ProjectService projectService;

    public Map<String, Object> userDashboard() {
        SysUser current = authService.currentUser();
        List<ProjectMember> memberships = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getUserId, current.getId()));
        List<Task> myTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, current.getId()));
        long done = myTasks.stream().filter(task -> "DONE".equalsIgnoreCase(task.getStatus())).count();
        long overdue = myTasks.stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now()))
                .filter(task -> !"DONE".equalsIgnoreCase(task.getStatus()))
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectCount", memberships.stream().map(ProjectMember::getProjectId).distinct().count());
        data.put("taskCount", myTasks.size());
        data.put("doneTaskCount", done);
        data.put("overdueTaskCount", overdue);
        data.put("recentTasks", taskBizService.myTasks().stream().limit(6).toList());
        return data;
    }

    public Map<String, Object> projectDashboard(Long projectId) {
        projectService.getProject(projectId);
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId));
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (String status : List.of("TODO", "IN_PROGRESS", "REVIEW", "DONE", "BLOCKED")) {
            statusCounts.put(status, tasks.stream().filter(task -> status.equalsIgnoreCase(task.getStatus())).count());
        }

        List<TaskVO> recentTasks = taskBizService.listByProject(projectId).stream().limit(8).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskCount", tasks.size());
        data.put("statusCounts", statusCounts);
        data.put("memberCount", projectMemberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)));
        data.put("completionRate", tasks.isEmpty() ? 100 : Math.round(statusCounts.get("DONE") * 100.0 / tasks.size()));
        data.put("completeness", statisticsService.completeness(projectId));
        data.put("contribution", statisticsService.contribution(projectId));
        data.put("riskTrend", statisticsService.riskTrend(projectId));
        data.put("recentTasks", recentTasks);
        return data;
    }

}
