package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.TemplateUpdateDTO;
import com.example.teamflow.entity.ProjectTemplate;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.ProjectTemplateMapper;
import com.example.teamflow.vo.ProjectTemplateVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectTemplateService {
    private final ProjectTemplateMapper projectTemplateMapper;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public List<ProjectTemplateVO> listEnabled() {
        return projectTemplateMapper.selectList(new LambdaQueryWrapper<ProjectTemplate>()
                        .eq(ProjectTemplate::getEnabled, 1)
                        .orderByAsc(ProjectTemplate::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public List<ProjectTemplateVO> adminListAll() {
        SysUser current = authService.currentUser();
        if (current == null || !"ADMIN".equalsIgnoreCase(current.getRole())) {
            throw new BizException(403, "仅管理员可查看全部项目模板");
        }
        return projectTemplateMapper.selectList(new LambdaQueryWrapper<ProjectTemplate>()
                        .orderByAsc(ProjectTemplate::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public ProjectTemplateVO detail(String code) {
        ProjectTemplate template = projectTemplateMapper.selectOne(new LambdaQueryWrapper<ProjectTemplate>()
                .eq(ProjectTemplate::getCode, normalize(code))
                .eq(ProjectTemplate::getEnabled, 1)
                .last("limit 1"));
        if (template == null) {
            throw new BizException(404, "项目模板不存在或已停用");
        }
        return toVO(template);
    }

    @Transactional
    public ProjectTemplateVO adminUpdate(Long id, TemplateUpdateDTO dto) {
        SysUser current = authService.currentUser();
        if (current == null || !"ADMIN".equalsIgnoreCase(current.getRole())) {
            throw new BizException(403, "仅管理员可维护项目模板");
        }
        ProjectTemplate template = projectTemplateMapper.selectById(id);
        if (template == null) {
            throw new BizException(404, "项目模板不存在");
        }
        if (dto == null) {
            throw new BizException(400, "模板更新内容不能为空");
        }

        if (StringUtils.hasText(dto.getName())) {
            template.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            template.setDescription(dto.getDescription());
        }
        if (dto.getRoleDefaults() != null) {
            validateArrayJson(dto.getRoleDefaults(), "角色默认配置");
            validateRoleDefaults(dto.getRoleDefaults());
            template.setRoleDefaults(dto.getRoleDefaults());
        }
        if (dto.getSectionDefaults() != null) {
            validateArrayJson(dto.getSectionDefaults(), "章节默认配置");
            validateSectionDefaults(dto.getSectionDefaults());
            template.setSectionDefaults(dto.getSectionDefaults());
        }
        if (dto.getPermissionDefaults() != null) {
            validateObjectJson(dto.getPermissionDefaults(), "权限默认配置");
            validatePermissionDefaults(dto.getPermissionDefaults());
            template.setPermissionDefaults(dto.getPermissionDefaults());
        }
        if (dto.getAiCheckRules() != null) {
            validateJson(dto.getAiCheckRules(), "AI 检查规则");
            template.setAiCheckRules(dto.getAiCheckRules());
        }
        if (dto.getEnabled() != null) {
            template.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        }
        template.setUpdateTime(LocalDateTime.now());
        projectTemplateMapper.updateById(template);
        return toVO(template);
    }

    public ProjectTemplateVO toVO(ProjectTemplate template) {
        ProjectTemplateVO vo = new ProjectTemplateVO();
        vo.setId(template.getId());
        vo.setCode(template.getCode());
        vo.setName(template.getName());
        vo.setMode(template.getMode());
        vo.setDescription(template.getDescription());
        vo.setRoleDefaults(template.getRoleDefaults());
        vo.setSectionDefaults(template.getSectionDefaults());
        vo.setPermissionDefaults(template.getPermissionDefaults());
        vo.setAiCheckRules(template.getAiCheckRules());
        vo.setEnabled(template.getEnabled());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }

    private void validateJson(String json, String label) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(400, label + "不能为空");
        }
        try {
            objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new BizException(400, label + "不是合法 JSON");
        }
    }

    private void validateArrayJson(String json, String label) {
        JsonNode root = readJson(json, label);
        if (!root.isArray() || root.isEmpty()) {
            throw new BizException(400, label + "必须是非空 JSON 数组");
        }
    }

    private void validateObjectJson(String json, String label) {
        JsonNode root = readJson(json, label);
        if (!root.isObject()) {
            throw new BizException(400, label + "必须是 JSON 对象");
        }
    }

    private void validateRoleDefaults(String json) {
        JsonNode root = readJson(json, "角色默认配置");
        for (JsonNode node : root) {
            if (!hasText(node, "code", "roleCode") || !hasText(node, "name", "roleName")) {
                throw new BizException(400, "角色默认配置每一项都必须包含 code/name");
            }
        }
    }

    private void validateSectionDefaults(String json) {
        JsonNode root = readJson(json, "章节默认配置");
        for (JsonNode node : root) {
            if (!hasText(node, "code", "sectionCode") || !hasText(node, "name", "sectionName")) {
                throw new BizException(400, "章节默认配置每一项都必须包含 code/name");
            }
        }
    }

    private void validatePermissionDefaults(String json) {
        JsonNode root = readJson(json, "权限默认配置");
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        if (!fields.hasNext()) {
            throw new BizException(400, "权限默认配置不能为空");
        }
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode permissions = entry.getValue();
            if (!StringUtils.hasText(entry.getKey()) || !permissions.isArray() || permissions.isEmpty()) {
                throw new BizException(400, "权限默认配置每个角色都必须配置非空权限数组");
            }
            for (JsonNode permission : permissions) {
                if (!permission.isTextual() || !StringUtils.hasText(permission.asText())) {
                    throw new BizException(400, "权限默认配置不能包含空权限编码");
                }
            }
        }
    }

    private JsonNode readJson(String json, String label) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(400, label + "不能为空");
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new BizException(400, label + "不是合法 JSON");
        }
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return true;
            }
        }
        return false;
    }
}
