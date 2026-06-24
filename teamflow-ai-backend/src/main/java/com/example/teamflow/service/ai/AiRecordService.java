package com.example.teamflow.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.entity.AiRecord;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.AiRecordMapper;
import com.example.teamflow.mapper.ProjectMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.service.AuthService;
import com.example.teamflow.service.ProjectService;
import com.example.teamflow.vo.AiRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiRecordService {
    private final AiRecordMapper aiRecordMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final ProjectService projectService;

    public List<AiRecordVO> records(Long projectId) {
        projectService.getProject(projectId);
        return aiRecordMapper.selectList(new LambdaQueryWrapper<AiRecord>()
                        .eq(AiRecord::getProjectId, projectId)
                        .orderByDesc(AiRecord::getCreateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public AiRecord saveGeneratedRecord(Long projectId, String type, String prompt, String result, String source, String modelName, String riskLevel) {
        LocalDateTime now = LocalDateTime.now();
        AiRecord record = new AiRecord();
        record.setProjectId(projectId);
        record.setUserId(authService.currentUser().getId());
        record.setType(type);
        record.setPrompt(prompt);
        record.setResult(result);
        record.setStatus("GENERATED");
        record.setConfirmedFlag(0);
        record.setSource(source);
        record.setModelName(modelName);
        record.setRiskLevel(riskLevel);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        aiRecordMapper.insert(record);
        return record;
    }

    public AiRecordVO toVO(AiRecord record) {
        AiRecordVO vo = new AiRecordVO();
        vo.setId(record.getId());
        vo.setProjectId(record.getProjectId());
        vo.setUserId(record.getUserId());
        Project project = record.getProjectId() == null ? null : projectMapper.selectById(record.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        SysUser user = sysUserMapper.selectById(record.getUserId());
        vo.setUsername(user == null ? "" : user.getUsername());
        vo.setType(record.getType());
        vo.setPrompt(record.getPrompt());
        vo.setResult(record.getResult());
        vo.setStatus(record.getStatus());
        vo.setConfirmedFlag(record.getConfirmedFlag());
        vo.setSource(record.getSource());
        vo.setModelName(record.getModelName());
        vo.setRiskLevel(record.getRiskLevel());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }
}
