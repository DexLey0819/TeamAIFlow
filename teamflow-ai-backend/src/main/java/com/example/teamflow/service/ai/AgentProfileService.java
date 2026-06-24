package com.example.teamflow.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.AgentProfileUpdateDTO;
import com.example.teamflow.entity.AgentProfile;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.AgentProfileMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.service.AuthService;
import com.example.teamflow.service.PermissionService;
import com.example.teamflow.vo.AgentProfileVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentProfileService {
    public static final String SOURCE_PROJECT = "PROJECT";
    public static final String SOURCE_GLOBAL = "GLOBAL";
    public static final String SOURCE_SYNTHETIC = "SYNTHETIC";

    private static final String SCOPE_PROJECT = "PROJECT";
    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final long GLOBAL_SCOPE_ID = 0L;
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final Set<String> ALLOWED_CONTEXTS = Set.of(
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
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "READ_CONTEXT",
            "GENERATE_DRAFT",
            "SUGGEST_REVIEW_COMMENT",
            "INSPECT_FILES",
            "SUGGEST_TASKS"
    );
    private static final Set<String> ALLOWED_TEMPLATE_VARIABLES = Set.of(
            "roleCode",
            "roleName",
            "responsibilities",
            "projectName",
            "projectDescription",
            "sectionName",
            "sectionCode",
            "sectionDescription",
            "context",
            "outputTemplate",
            "toolPermissions"
    );
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    private final AgentProfileMapper agentProfileMapper;
    private final ProjectRoleMapper projectRoleMapper;
    private final PermissionService permissionService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public EffectiveAgentProfile resolveEffective(Long projectId, Long projectRoleId) {
        ProjectRole role = findProjectRole(projectId, projectRoleId);
        return resolveEffective(projectId, role);
    }

    public List<AgentProfileVO> listVisible(Long projectId) {
        permissionService.requireProjectMember(projectId);
        List<ProjectRole> roles = projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                .eq(ProjectRole::getProjectId, projectId)
                .orderByAsc(ProjectRole::getSortOrder)
                .orderByAsc(ProjectRole::getId));

        return roles.stream()
                .sorted(Comparator.comparing(
                                ProjectRole::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProjectRole::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(role -> toVisible(resolveEffective(projectId, role), false))
                .toList();
    }

    public AgentProfileVO getVisible(Long projectId, Long projectRoleId) {
        permissionService.requireProjectMember(projectId);
        EffectiveAgentProfile profile = resolveEffective(projectId, projectRoleId);
        SysUser current = authService.currentUser();
        boolean managerView = permissionService.isProjectManager(projectId, current.getId());
        return toVisible(profile, managerView);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentProfileVO saveProjectSnapshot(
            Long projectId,
            Long projectRoleId,
            AgentProfileUpdateDTO dto
    ) {
        permissionService.requireProjectManage(projectId);
        ProjectRole role = findProjectRole(projectId, projectRoleId);
        validateUpdate(dto);

        AgentProfile stored = findProjectProfile(projectId, role.getRoleCode());
        if (stored != null) {
            applySnapshot(stored, projectId, role, dto, false);
            int affectedRows = agentProfileMapper.updateById(stored);
            AgentProfile persisted = resolveAfterUpdate(affectedRows, projectId, role.getRoleCode(), stored);
            return toVisible(toEffective(persisted, role, SOURCE_PROJECT), true);
        }

        AgentProfile created = new AgentProfile();
        applySnapshot(created, projectId, role, dto, true);
        try {
            agentProfileMapper.insert(created);
            return toVisible(toEffective(created, role, SOURCE_PROJECT), true);
        } catch (DuplicateKeyException exception) {
            AgentProfile raced = findProjectProfile(projectId, role.getRoleCode());
            if (raced == null) {
                throw new BizException(409, "角色智能体配置已被并发修改");
            }
            applySnapshot(raced, projectId, role, dto, false);
            int affectedRows = agentProfileMapper.updateById(raced);
            AgentProfile persisted = resolveAfterUpdate(affectedRows, projectId, role.getRoleCode(), raced);
            return toVisible(toEffective(persisted, role, SOURCE_PROJECT), true);
        }
    }

    @Transactional
    public AgentProfileVO resetProjectSnapshot(Long projectId, Long projectRoleId) {
        permissionService.requireProjectManage(projectId);
        ProjectRole role = findProjectRole(projectId, projectRoleId);
        AgentProfile stored = findProjectProfile(projectId, role.getRoleCode());
        if (stored != null) {
            agentProfileMapper.deleteById(stored.getId());
        }
        return toVisible(resolveEffective(projectId, role), true);
    }

    private ProjectRole findProjectRole(Long projectId, Long projectRoleId) {
        ProjectRole role = projectRoleId == null ? null : projectRoleMapper.selectById(projectRoleId);
        if (role == null || !Objects.equals(projectId, role.getProjectId())) {
            throw new BizException(404, "项目角色不存在");
        }
        return role;
    }

    private EffectiveAgentProfile resolveEffective(Long projectId, ProjectRole role) {
        AgentProfile projectProfile = findProjectProfile(projectId, role.getRoleCode());
        if (projectProfile != null) {
            return toEffective(projectProfile, role, SOURCE_PROJECT);
        }

        AgentProfile globalProfile = agentProfileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getScopeType, SCOPE_GLOBAL)
                .eq(AgentProfile::getScopeId, GLOBAL_SCOPE_ID)
                .eq(AgentProfile::getRoleCode, role.getRoleCode())
                .last("limit 1"));
        if (globalProfile != null) {
            return toEffective(globalProfile, role, SOURCE_GLOBAL);
        }
        return syntheticProfile(projectId, role);
    }

    private AgentProfile findProjectProfile(Long projectId, String roleCode) {
        return agentProfileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getScopeType, SCOPE_PROJECT)
                .eq(AgentProfile::getScopeId, projectId)
                .eq(AgentProfile::getRoleCode, roleCode)
                .last("limit 1"));
    }

    private AgentProfile resolveAfterUpdate(
            int affectedRows,
            Long projectId,
            String roleCode,
            AgentProfile updated
    ) {
        if (affectedRows > 0) {
            return updated;
        }
        AgentProfile persisted = findProjectProfile(projectId, roleCode);
        if (persisted == null) {
            throw new BizException(409, "角色智能体配置已被并发修改");
        }
        return persisted;
    }

    private EffectiveAgentProfile syntheticProfile(Long projectId, ProjectRole role) {
        EffectiveAgentProfile profile = new EffectiveAgentProfile();
        profile.setProjectId(projectId);
        profile.setProjectRoleId(role.getId());
        profile.setRoleCode(role.getRoleCode());
        profile.setRoleName(role.getRoleName());
        profile.setResponsibilities(role.getResponsibility());
        profile.setContextScope(List.of("PROJECT_OVERVIEW", "CURRENT_SECTION", "PREVIOUS_SECTIONS"));
        profile.setOutputTemplate("围绕当前板块输出结构清晰、可执行、可验证的内容。");
        profile.setToolPermissions(List.of("READ_CONTEXT", "GENERATE_DRAFT"));
        profile.setMemoryPolicy(Map.of("enabled", false));
        profile.setSystemPrompt("你是 TeamFlowAI 的" + role.getRoleName() + "角色智能体。只基于授权上下文工作，不编造事实。");
        profile.setTaskPromptTemplate(
                "请以${roleName}身份为${projectName}的${sectionName}生成内容。职责：${responsibilities}。"
                        + "上下文：${context}。输出要求：${outputTemplate}。允许能力：${toolPermissions}。"
        );
        profile.setMultimodalConfig(Map.of("enabled", false, "inputTypes", List.of()));
        profile.setEnabled(true);
        profile.setSource(SOURCE_SYNTHETIC);
        return profile;
    }

    private EffectiveAgentProfile toEffective(AgentProfile stored, ProjectRole role, String source) {
        EffectiveAgentProfile profile = new EffectiveAgentProfile();
        profile.setId(stored.getId());
        profile.setProjectId(role.getProjectId());
        profile.setProjectRoleId(role.getId());
        profile.setRoleCode(stored.getRoleCode());
        profile.setRoleName(stored.getRoleName());
        profile.setResponsibilities(stored.getResponsibilities());
        profile.setContextScope(readJson(stored.getContextScope(), STRING_LIST_TYPE));
        profile.setOutputTemplate(stored.getOutputTemplate());
        profile.setToolPermissions(readJson(stored.getToolPermissions(), STRING_LIST_TYPE));
        profile.setMemoryPolicy(readJson(stored.getMemoryPolicy(), OBJECT_MAP_TYPE));
        profile.setSystemPrompt(stored.getSystemPrompt());
        profile.setTaskPromptTemplate(stored.getTaskPromptTemplate());
        profile.setMultimodalConfig(readJson(stored.getMultimodalConfig(), OBJECT_MAP_TYPE));
        profile.setEnabled(Integer.valueOf(1).equals(stored.getEnabled()));
        profile.setSource(source);
        profile.setUpdateTime(stored.getUpdateTime());
        return profile;
    }

    private AgentProfileVO toVisible(EffectiveAgentProfile profile, boolean fullDetail) {
        AgentProfileVO visible = new AgentProfileVO();
        visible.setId(profile.getId());
        visible.setProjectId(profile.getProjectId());
        visible.setProjectRoleId(profile.getProjectRoleId());
        visible.setRoleCode(profile.getRoleCode());
        visible.setRoleName(profile.getRoleName());
        visible.setResponsibilities(profile.getResponsibilities());
        visible.setContextScope(profile.getContextScope());
        visible.setOutputTemplate(profile.getOutputTemplate());
        visible.setToolPermissions(profile.getToolPermissions());
        visible.setMemoryPolicy(profile.getMemoryPolicy());
        visible.setSystemPrompt(profile.getSystemPrompt());
        visible.setTaskPromptTemplate(profile.getTaskPromptTemplate());
        visible.setMultimodalConfig(profile.getMultimodalConfig());
        visible.setEnabled(profile.isEnabled());
        visible.setSource(profile.getSource());
        visible.setUpdateTime(profile.getUpdateTime());
        if (!fullDetail) {
            visible.setSystemPrompt(null);
            visible.setTaskPromptTemplate(null);
            visible.setMemoryPolicy(null);
            visible.setMultimodalConfig(null);
        }
        return visible;
    }

    private void applySnapshot(
            AgentProfile target,
            Long projectId,
            ProjectRole role,
            AgentProfileUpdateDTO dto,
            boolean create
    ) {
        LocalDateTime now = LocalDateTime.now();
        target.setScopeType(SCOPE_PROJECT);
        target.setScopeId(projectId);
        target.setProjectId(projectId);
        target.setProjectRoleId(role.getId());
        target.setRoleCode(role.getRoleCode());
        target.setRoleName(role.getRoleName());
        target.setResponsibilities(dto.getResponsibilities());
        target.setContextScope(writeJson(dto.getContextScope()));
        target.setOutputTemplate(dto.getOutputTemplate());
        target.setToolPermissions(writeJson(dto.getToolPermissions()));
        target.setMemoryPolicy(writeJson(dto.getMemoryPolicy()));
        target.setSystemPrompt(dto.getSystemPrompt());
        target.setTaskPromptTemplate(dto.getTaskPromptTemplate());
        target.setMultimodalConfig(writeJson(dto.getMultimodalConfig()));
        target.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        if (create) {
            target.setCreateTime(now);
        }
        target.setUpdateTime(now);
    }

    private void validateUpdate(AgentProfileUpdateDTO dto) {
        if (dto == null) {
            throw new BizException(400, "角色智能体配置不能为空");
        }
        requireText(dto.getResponsibilities(), 4000, "角色智能体职责不合法");
        if (dto.getContextScope() == null
                || dto.getContextScope().isEmpty()
                || dto.getContextScope().size() > 9
                || dto.getContextScope().stream().anyMatch(Objects::isNull)
                || !ALLOWED_CONTEXTS.containsAll(dto.getContextScope())) {
            throw new BizException(400, "角色智能体上下文范围不合法");
        }
        requireText(dto.getOutputTemplate(), 6000, "角色智能体输出模板不合法");
        if (dto.getToolPermissions() == null
                || dto.getToolPermissions().size() > 5
                || dto.getToolPermissions().stream().anyMatch(Objects::isNull)
                || !ALLOWED_TOOLS.containsAll(dto.getToolPermissions())) {
            throw new BizException(400, "角色智能体工具权限不合法");
        }
        if (dto.getMemoryPolicy() == null) {
            throw new BizException(400, "角色智能体记忆策略不合法");
        }
        requireText(dto.getSystemPrompt(), 12000, "角色智能体系统提示词不合法");
        requireText(dto.getTaskPromptTemplate(), 16000, "角色智能体任务模板不合法");
        if (dto.getMultimodalConfig() == null) {
            throw new BizException(400, "角色智能体多模态配置不合法");
        }
        if (dto.getEnabled() == null) {
            throw new BizException(400, "角色智能体启用状态不能为空");
        }
        validateTemplateVariables(dto.getOutputTemplate());
        validateTemplateVariables(dto.getSystemPrompt());
        validateTemplateVariables(dto.getTaskPromptTemplate());

        writeJson(dto.getContextScope());
        writeJson(dto.getToolPermissions());
        writeJson(dto.getMemoryPolicy());
        writeJson(dto.getMultimodalConfig());
    }

    private void requireText(String value, int maximumLength, String message) {
        if (!StringUtils.hasText(value) || value.length() > maximumLength) {
            throw new BizException(400, message);
        }
    }

    private void validateTemplateVariables(String template) {
        Matcher matcher = TEMPLATE_VARIABLE.matcher(template);
        int checkedThrough = 0;
        while (matcher.find()) {
            if (template.substring(checkedThrough, matcher.start()).contains("${")
                    || !ALLOWED_TEMPLATE_VARIABLES.contains(matcher.group(1))) {
                throw new BizException(400, "角色智能体模板变量不合法");
            }
            checkedThrough = matcher.end();
        }
        if (template.substring(checkedThrough).contains("${")) {
            throw new BizException(400, "角色智能体模板变量不合法");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(400, "角色智能体配置无法序列化");
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            if (!StringUtils.hasText(json)) {
                throw new IllegalArgumentException("blank json");
            }
            T value = objectMapper.readValue(json, type);
            if (value == null) {
                throw new IllegalArgumentException("null json");
            }
            return value;
        } catch (Exception exception) {
            throw new BizException(500, "角色智能体配置数据损坏");
        }
    }
}
