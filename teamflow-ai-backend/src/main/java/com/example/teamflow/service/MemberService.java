package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.vo.ProjectMemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final ProjectService projectService;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectRoleMapper projectRoleMapper;
    private final SysUserMapper sysUserMapper;

    public List<ProjectMemberVO> list(Long projectId) {
        projectService.getProject(projectId);
        return projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .orderByAsc(ProjectMember::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private ProjectMemberVO toVO(ProjectMember member) {
        SysUser user = member.getUserId() == null ? null : sysUserMapper.selectById(member.getUserId());
        ProjectRole role = member.getProjectRoleId() == null ? null : projectRoleMapper.selectById(member.getProjectRoleId());
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(member.getId());
        vo.setProjectId(member.getProjectId());
        vo.setUserId(member.getUserId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setRealName(user == null ? null : user.getRealName());
        vo.setEmail(user == null ? null : user.getEmail());
        vo.setProjectRoleId(member.getProjectRoleId());
        vo.setMemberRole(member.getMemberRole());
        vo.setMemberTitle(member.getMemberTitle() == null && role != null ? role.getRoleName() : member.getMemberTitle());
        vo.setJoinSource(member.getJoinSource());
        vo.setJoinTime(member.getJoinTime());
        return vo;
    }
}
