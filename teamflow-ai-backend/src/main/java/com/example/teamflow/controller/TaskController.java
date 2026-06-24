package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.TaskDTO;
import com.example.teamflow.dto.TaskStatusDTO;
import com.example.teamflow.service.TaskBizService;
import com.example.teamflow.vo.TaskLogVO;
import com.example.teamflow.vo.TaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {
    private final TaskBizService taskBizService;

    @GetMapping("/projects/{id}/tasks")
    public Result<List<TaskVO>> listByProject(@PathVariable Long id) {
        return Result.success(taskBizService.listByProject(id));
    }

    @PostMapping("/tasks")
    public Result<TaskVO> create(@Valid @RequestBody TaskDTO dto) {
        return Result.success(taskBizService.create(dto));
    }

    @PutMapping("/tasks/{id}")
    public Result<TaskVO> update(@PathVariable Long id, @Valid @RequestBody TaskDTO dto) {
        return Result.success(taskBizService.update(id, dto));
    }

    @PutMapping("/tasks/{id}/status")
    public Result<TaskVO> updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusDTO dto) {
        return Result.success(taskBizService.updateStatus(id, dto));
    }

    @GetMapping("/tasks/{id}/logs")
    public Result<List<TaskLogVO>> logs(@PathVariable Long id) {
        return Result.success(taskBizService.logs(id));
    }

}
