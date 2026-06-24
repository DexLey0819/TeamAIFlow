package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.ProjectInitDTO;
import com.example.teamflow.dto.ReportRuleDTO;
import com.example.teamflow.dto.RoleHeadcountDTO;
import com.example.teamflow.entity.ProgressReportRule;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectMember;
import com.example.teamflow.entity.ProjectRole;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.ProjectTemplate;
import com.example.teamflow.entity.RiskSnapshot;
import com.example.teamflow.entity.RoleSectionPermission;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.entity.Task;
import com.example.teamflow.mapper.ProgressReportRuleMapper;
import com.example.teamflow.mapper.ProjectMapper;
import com.example.teamflow.mapper.ProjectMemberMapper;
import com.example.teamflow.mapper.ProjectRoleMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.ProjectTemplateMapper;
import com.example.teamflow.mapper.RiskSnapshotMapper;
import com.example.teamflow.mapper.RoleSectionPermissionMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.mapper.TaskMapper;
import com.example.teamflow.vo.PermissionMatrixVO;
import com.example.teamflow.vo.ProjectInitResultVO;
import com.example.teamflow.vo.ProjectRoleVO;
import com.example.teamflow.vo.ProjectVO;
import com.example.teamflow.vo.SectionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    private static final List<String> ALL_PERMISSIONS = List.of(
            "VIEW", "EDIT", "COMMENT", "REVIEW", "MANAGE", "EXPORT", "AI_GENERATE"
    );

    private final ProjectMapper projectMapper;
    private final ProjectTemplateMapper projectTemplateMapper;
    private final ProjectRoleMapper projectRoleMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final RoleSectionPermissionMapper roleSectionPermissionMapper;
    private final ProgressReportRuleMapper progressReportRuleMapper;
    private final SysUserMapper sysUserMapper;
    private final TaskMapper taskMapper;
    private final RiskSnapshotMapper riskSnapshotMapper;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProjectInitResultVO init(ProjectInitDTO dto) {
        SysUser current = authService.currentUser();
        ProjectTemplate template = projectTemplateMapper.selectOne(new LambdaQueryWrapper<ProjectTemplate>()
                .eq(ProjectTemplate::getCode, normalize(dto.getTemplateCode()))
                .eq(ProjectTemplate::getEnabled, 1)
                .last("limit 1"));
        if (template == null) {
            throw new BizException(404, "项目模板不存在或已停用");
        }
        if (dto.getEndDate() != null && dto.getStartDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BizException(400, "结束日期不能早于开始日期");
        }

        Project project = new Project();
        project.setTemplateId(template.getId());
        project.setTemplateMode(template.getMode());
        project.setProjectCode(generateProjectCode());
        project.setProjectName(dto.getProjectName());
        project.setDescription(dto.getDescription());
        project.setStatus("PLANNING");
        project.setJoinPolicy(Boolean.FALSE.equals(dto.getAllowJoinApply()) ? "INVITE_ONLY" : "REVIEW");
        project.setJoinLink("/join/" + project.getProjectCode());
        project.setArchivedFlag(0);
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        project.setCreatorId(current.getId());
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.insert(project);

        List<RoleSpec> roleSpecs = resolveRoleSpecs(dto, template);
        List<ProjectRole> roles = createRoles(project.getId(), roleSpecs);
        ProjectRole managerRole = roles.stream()
                .filter(role -> PROJECT_MANAGER.equalsIgnoreCase(role.getRoleCode()))
                .findFirst()
                .orElseThrow(() -> new BizException(500, "项目经理角色初始化失败"));
        createManagerMembership(project, managerRole, current);

        List<ProjectSection> sections = createSections(project.getId(), template, roles);
        createPermissions(project.getId(), template, roles, sections);
        createReportRule(project.getId(), dto.getReportRule());

        return initResult(project.getId());
    }

    public List<ProjectVO> myProjects() {
        SysUser current = authService.currentUser();
        if (isAdmin(current)) {
            return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                            .orderByDesc(Project::getCreateTime))
                    .stream()
                    .map(this::toProjectVO)
                    .toList();
        }

        Set<Long> projectIds = new LinkedHashSet<>();
        projectMapper.selectList(new LambdaQueryWrapper<Project>().eq(Project::getCreatorId, current.getId()))
                .forEach(project -> projectIds.add(project.getId()));
        projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, current.getId()))
                .forEach(member -> projectIds.add(member.getProjectId()));
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return projectMapper.selectBatchIds(projectIds).stream()
                .sorted(Comparator.comparing(Project::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toProjectVO)
                .toList();
    }

    public ProjectVO detail(Long id) {
        return toProjectVO(getProject(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveWbs(Long id, String wbsData) {
        Project project = getProject(id);
        requireProjectAccess(project);
        project.setWbsData(wbsData);
        projectMapper.updateById(project);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateGithubRepo(Long id, String githubRepo) {
        Project project = getProject(id);
        ensureManager(project);
        project.setGithubRepo(githubRepo);
        projectMapper.updateById(project);
    }

    public ProjectInitResultVO initResult(Long projectId) {
        Project project = getProject(projectId);
        ProjectInitResultVO vo = new ProjectInitResultVO();
        vo.setProject(toProjectVO(project));
        vo.setProjectCode(project.getProjectCode());
        vo.setJoinLink(project.getJoinLink());
        vo.setRoles(listRoleVOs(projectId));
        vo.setSections(listSectionVOs(projectId));
        vo.setPermissionMatrix(permissionMatrix(projectId));
        return vo;
    }

    public Project getProject(Long id) {
        Project project = id == null ? null : projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(404, "项目不存在");
        }
        requireProjectAccess(project);
        return project;
    }

    public void ensureManager(Project project) {
        SysUser current = authService.currentUser();
        if (isAdmin(current) || Objects.equals(project.getCreatorId(), current.getId())) {
            return;
        }
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId())
                .eq(ProjectMember::getUserId, current.getId())
                .last("limit 1"));
        if (member != null && PROJECT_MANAGER.equalsIgnoreCase(member.getMemberRole())) {
            return;
        }
        throw new BizException(403, "仅项目经理可执行该操作");
    }

    private List<RoleSpec> resolveRoleSpecs(ProjectInitDTO dto, ProjectTemplate template) {
        List<RoleSpec> roleSpecs;
        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            roleSpecs = dto.getRoles().stream()
                    .map(this::fromRoleDTO)
                    .toList();
        } else {
            roleSpecs = parseRoleDefaults(template.getRoleDefaults());
            if (roleSpecs.isEmpty()) {
                throw new BizException(400, "项目模板角色默认配置不能为空");
            }
        }
        LinkedHashMap<String, RoleSpec> deduped = new LinkedHashMap<>();
        for (RoleSpec roleSpec : roleSpecs) {
            if (StringUtils.hasText(roleSpec.roleCode)) {
                roleSpec.roleCode = normalize(roleSpec.roleCode);
                deduped.putIfAbsent(roleSpec.roleCode, roleSpec);
            }
        }
        if (deduped.isEmpty()) {
            throw new BizException(400, "项目角色配置不能为空");
        }
        if (!deduped.containsKey(PROJECT_MANAGER)) {
            RoleSpec manager = new RoleSpec();
            manager.roleCode = PROJECT_MANAGER;
            manager.roleName = "项目经理";
            manager.responsibility = "负责项目初始化、成员协作和成果交付。";
            manager.maxCount = 1;
            manager.requiredFlag = true;
            manager.sortOrder = 0;
            LinkedHashMap<String, RoleSpec> withManager = new LinkedHashMap<>();
            withManager.put(PROJECT_MANAGER, manager);
            withManager.putAll(deduped);
            deduped = withManager;
        }
        List<RoleSpec> result = new ArrayList<>(deduped.values());
        for (int i = 0; i < result.size(); i++) {
            RoleSpec roleSpec = result.get(i);
            if (!StringUtils.hasText(roleSpec.roleName)) {
                roleSpec.roleName = roleSpec.roleCode;
            }
            if (roleSpec.maxCount == null || roleSpec.maxCount < 1) {
                roleSpec.maxCount = PROJECT_MANAGER.equals(roleSpec.roleCode) ? 1 : 99;
            }
            if (roleSpec.requiredFlag == null) {
                roleSpec.requiredFlag = true;
            }
            if (roleSpec.sortOrder == null) {
                roleSpec.sortOrder = i + 1;
            }
        }
        return result;
    }

    private List<ProjectRole> createRoles(Long projectId, List<RoleSpec> roleSpecs) {
        List<ProjectRole> roles = new ArrayList<>();
        for (RoleSpec spec : roleSpecs) {
            ProjectRole role = new ProjectRole();
            role.setProjectId(projectId);
            role.setRoleCode(spec.roleCode);
            role.setRoleName(spec.roleName);
            role.setResponsibility(spec.responsibility);
            role.setMaxCount(spec.maxCount);
            role.setCurrentCount(PROJECT_MANAGER.equals(spec.roleCode) ? 1 : 0);
            role.setRequiredFlag(Boolean.FALSE.equals(spec.requiredFlag) ? 0 : 1);
            role.setSortOrder(spec.sortOrder);
            role.setCreateTime(LocalDateTime.now());
            role.setUpdateTime(LocalDateTime.now());
            projectRoleMapper.insert(role);
            roles.add(role);
        }
        return roles;
    }

    private void createManagerMembership(Project project, ProjectRole managerRole, SysUser current) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(current.getId());
        member.setProjectRoleId(managerRole.getId());
        member.setMemberRole(PROJECT_MANAGER);
        member.setMemberTitle(managerRole.getRoleName());
        member.setJoinSource("INIT");
        member.setJoinTime(LocalDateTime.now());
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        projectMemberMapper.insert(member);
    }

    private List<ProjectSection> createSections(Long projectId, ProjectTemplate template, List<ProjectRole> roles) {
        Map<String, ProjectRole> roleByCode = roles.stream()
                .collect(Collectors.toMap(role -> normalize(role.getRoleCode()), Function.identity(), (left, right) -> left));
        List<SectionSpec> sectionSpecs = parseSectionDefaults(template.getSectionDefaults());
        if (sectionSpecs.isEmpty()) {
            throw new BizException(400, "项目模板章节默认配置不能为空");
        }
        List<ProjectSection> sections = new ArrayList<>();
        for (int i = 0; i < sectionSpecs.size(); i++) {
            SectionSpec spec = sectionSpecs.get(i);
            ProjectSection section = new ProjectSection();
            section.setProjectId(projectId);
            section.setSectionCode(normalize(spec.sectionCode));
            section.setSectionName(StringUtils.hasText(spec.sectionName) ? spec.sectionName : section.getSectionCode());
            section.setDescription(spec.description);
            section.setRequiredFlag(Boolean.FALSE.equals(spec.requiredFlag) ? 0 : 1);
            if (StringUtils.hasText(spec.ownerRoleCode)) {
                ProjectRole ownerRole = roleByCode.get(normalize(spec.ownerRoleCode));
                section.setOwnerRoleId(ownerRole == null ? null : ownerRole.getId());
            }
            section.setStatus("EMPTY");
            section.setSortOrder(spec.sortOrder == null ? i + 1 : spec.sortOrder);
            section.setCreateTime(LocalDateTime.now());
            section.setUpdateTime(LocalDateTime.now());
            projectSectionMapper.insert(section);
            sections.add(section);
        }
        return sections;
    }

    private void createPermissions(Long projectId, ProjectTemplate template, List<ProjectRole> roles, List<ProjectSection> sections) {
        int insertedCount = 0;
        boolean isFrontBackend = "FRONT_BACKEND".equalsIgnoreCase(template.getMode());

        for (ProjectRole role : roles) {
            String roleCode = normalize(role.getRoleCode());
            for (ProjectSection section : sections) {
                List<String> permissions;
                if (isFrontBackend) {
                    permissions = getFrontBackendPermissions(roleCode, normalize(section.getSectionCode()));
                } else {
                    Map<String, List<String>> defaultPermissions = parsePermissionDefaults(template.getPermissionDefaults());
                    List<String> roleDefaults = defaultPermissions.get(roleCode);
                    permissions = roleDefaults == null || roleDefaults.isEmpty()
                            ? fallbackPermissions(role, section)
                            : roleDefaults;
                }
                for (String permissionCode : distinctPermissions(permissions)) {
                    RoleSectionPermission permission = new RoleSectionPermission();
                    permission.setProjectId(projectId);
                    permission.setProjectRoleId(role.getId());
                    permission.setSectionId(section.getId());
                    permission.setPermissionCode(permissionCode);
                    permission.setCreateTime(LocalDateTime.now());
                    roleSectionPermissionMapper.insert(permission);
                    insertedCount++;
                }
            }
        }
        if (insertedCount == 0) {
            throw new BizException(400, "项目权限配置不能为空");
        }
    }

    private List<String> getFrontBackendPermissions(String roleCode, String sectionCode) {
        List<String> permissions = new ArrayList<>();
        // All roles have VIEW and COMMENT on all sections
        permissions.add("VIEW");
        permissions.add("COMMENT");

        // Project Manager always has EXPORT
        if ("PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            permissions.add("EXPORT");
        }

        // Determine Editor
        boolean isEditor = false;
        if ("REQUIREMENT".equalsIgnoreCase(sectionCode) && "PRODUCT_MANAGER".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("USE_CASE_ANALYSIS".equalsIgnoreCase(sectionCode) && "PRODUCT_MANAGER".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("PROTOTYPE".equalsIgnoreCase(sectionCode) && "PRODUCT_MANAGER".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("TECH_FRAMEWORK".equalsIgnoreCase(sectionCode) && "TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("DATABASE".equalsIgnoreCase(sectionCode) && "TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("USE_CASE".equalsIgnoreCase(sectionCode) && "TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("API".equalsIgnoreCase(sectionCode) && "TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("FRONTEND".equalsIgnoreCase(sectionCode) && "FRONTEND_DEV".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("BACKEND".equalsIgnoreCase(sectionCode) && "BACKEND_DEV".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("TESTING".equalsIgnoreCase(sectionCode) && "QA".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("SUMMARY".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        } else if ("MANAGEMENT_DELIVERY".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isEditor = true;
        }

        if (isEditor) {
            permissions.add("EDIT");
            permissions.add("AI_GENERATE");
        }

        // Determine Reviewer
        boolean isReviewer = false;
        if ("REQUIREMENT".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("USE_CASE_ANALYSIS".equalsIgnoreCase(sectionCode) && ("TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode) || "PROJECT_MANAGER".equalsIgnoreCase(roleCode))) {
            isReviewer = true;
        } else if ("PROTOTYPE".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("TECH_FRAMEWORK".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("DATABASE".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("USE_CASE".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("API".equalsIgnoreCase(sectionCode) && "PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("FRONTEND".equalsIgnoreCase(sectionCode) && "TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("BACKEND".equalsIgnoreCase(sectionCode) && "TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        } else if ("TESTING".equalsIgnoreCase(sectionCode) && ("TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode) || "PROJECT_MANAGER".equalsIgnoreCase(roleCode))) {
            isReviewer = true;
        } else if ("SUMMARY".equalsIgnoreCase(sectionCode) && ("PRODUCT_MANAGER".equalsIgnoreCase(roleCode) || "TECHNICAL_DIRECTOR".equalsIgnoreCase(roleCode))) {
            isReviewer = true;
        } else if ("MANAGEMENT_DELIVERY".equalsIgnoreCase(sectionCode) && !"PROJECT_MANAGER".equalsIgnoreCase(roleCode)) {
            isReviewer = true;
        }

        if (isReviewer) {
            permissions.add("REVIEW");
        }

        return permissions;
    }

    private void createReportRule(Long projectId, ReportRuleDTO dto) {
        ProgressReportRule rule = new ProgressReportRule();
        rule.setProjectId(projectId);
        rule.setFrequency(dto != null && StringUtils.hasText(dto.getFrequency()) ? dto.getFrequency() : "WEEKLY");
        rule.setReportDay(dto != null && StringUtils.hasText(dto.getReportDay()) ? dto.getReportDay() : "SUNDAY");
        rule.setReportTime(dto != null && StringUtils.hasText(dto.getReportTime()) ? dto.getReportTime() : "22:00");
        rule.setRequiredFlag(dto == null || !Boolean.FALSE.equals(dto.getRequiredFlag()) ? 1 : 0);
        rule.setOverduePolicy(dto != null && StringUtils.hasText(dto.getOverduePolicy())
                ? dto.getOverduePolicy()
                : "每周日晚 22:00 前提交，逾期标记为 LATE；补交后标记为 SUPPLEMENTED。");
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        progressReportRuleMapper.insert(rule);
    }

    private void requireProjectAccess(Project project) {
        SysUser current = authService.currentUser();
        if (isAdmin(current)
                || Objects.equals(project.getCreatorId(), current.getId())) {
            return;
        }
        Long count = projectMemberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId())
                .eq(ProjectMember::getUserId, current.getId()));
        if (count != null && count > 0) {
            return;
        }
        throw new BizException(403, "没有权限访问该项目");
    }

    private ProjectVO toProjectVO(Project project) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setTemplateId(project.getTemplateId());
        vo.setTemplateMode(project.getTemplateMode());
        vo.setProjectCode(project.getProjectCode());
        vo.setProjectName(project.getProjectName());
        vo.setDescription(project.getDescription());
        vo.setStatus(project.getStatus());
        vo.setJoinPolicy(project.getJoinPolicy());
        vo.setJoinLink(project.getJoinLink());
        vo.setArchivedFlag(project.getArchivedFlag());
        vo.setStartDate(project.getStartDate());
        vo.setEndDate(project.getEndDate());
        vo.setCreatorId(project.getCreatorId());
        vo.setCreateTime(project.getCreateTime());
        vo.setUpdateTime(project.getUpdateTime());
        vo.setWbsData(project.getWbsData());
        vo.setGithubRepo(project.getGithubRepo());

        SysUser creator = project.getCreatorId() == null ? null : sysUserMapper.selectById(project.getCreatorId());
        vo.setCreatorName(creator == null ? "" : creator.getRealName());

        Long memberCount = projectMemberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, project.getId()));
        Long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<Task>().eq(Task::getProjectId, project.getId()));
        Long doneTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, project.getId())
                .eq(Task::getStatus, "DONE"));
        Long sectionCount = projectSectionMapper.selectCount(new LambdaQueryWrapper<ProjectSection>().eq(ProjectSection::getProjectId, project.getId()));
        Long approvedSectionCount = projectSectionMapper.selectCount(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, project.getId())
                .eq(ProjectSection::getStatus, "APPROVED"));
        vo.setMemberCount(toInt(memberCount));
        vo.setTaskCount(toInt(taskCount));
        vo.setDoneTaskCount(toInt(doneTaskCount));
        vo.setSectionCount(toInt(sectionCount));
        vo.setApprovedSectionCount(toInt(approvedSectionCount));
        vo.setCompletenessScore(sectionCount == null || sectionCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(approvedSectionCount == null ? 0 : approvedSectionCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(sectionCount), 2, RoundingMode.HALF_UP));

        RiskSnapshot risk = riskSnapshotMapper.selectOne(new LambdaQueryWrapper<RiskSnapshot>()
                .eq(RiskSnapshot::getProjectId, project.getId())
                .orderByDesc(RiskSnapshot::getSnapshotTime)
                .orderByDesc(RiskSnapshot::getCreateTime)
                .last("limit 1"));
        vo.setRiskLevel(risk == null ? null : risk.getRiskLevel());
        return vo;
    }

    private List<ProjectRoleVO> listRoleVOs(Long projectId) {
        return projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                        .eq(ProjectRole::getProjectId, projectId)
                        .orderByAsc(ProjectRole::getSortOrder)
                        .orderByAsc(ProjectRole::getId))
                .stream()
                .map(this::toRoleVO)
                .toList();
    }

    private List<SectionVO> listSectionVOs(Long projectId) {
        Map<Long, ProjectRole> roleById = projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                        .eq(ProjectRole::getProjectId, projectId))
                .stream()
                .collect(Collectors.toMap(ProjectRole::getId, Function.identity(), (left, right) -> left));
        return projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                        .eq(ProjectSection::getProjectId, projectId)
                        .orderByAsc(ProjectSection::getSortOrder)
                        .orderByAsc(ProjectSection::getId))
                .stream()
                .map(section -> toSectionVO(section, roleById))
                .toList();
    }

    private PermissionMatrixVO permissionMatrix(Long projectId) {
        List<ProjectRole> roles = projectRoleMapper.selectList(new LambdaQueryWrapper<ProjectRole>()
                .eq(ProjectRole::getProjectId, projectId)
                .orderByAsc(ProjectRole::getSortOrder)
                .orderByAsc(ProjectRole::getId));
        List<ProjectSection> sections = projectSectionMapper.selectList(new LambdaQueryWrapper<ProjectSection>()
                .eq(ProjectSection::getProjectId, projectId)
                .orderByAsc(ProjectSection::getSortOrder)
                .orderByAsc(ProjectSection::getId));
        List<RoleSectionPermission> permissions = roleSectionPermissionMapper.selectList(new LambdaQueryWrapper<RoleSectionPermission>()
                .eq(RoleSectionPermission::getProjectId, projectId)
                .orderByAsc(RoleSectionPermission::getProjectRoleId)
                .orderByAsc(RoleSectionPermission::getSectionId));

        PermissionMatrixVO vo = new PermissionMatrixVO();
        vo.setRoleRows(roles.stream().map(role -> {
            PermissionMatrixVO.RoleRow row = new PermissionMatrixVO.RoleRow();
            row.setRoleId(role.getId());
            row.setRoleCode(role.getRoleCode());
            row.setRoleName(role.getRoleName());
            return row;
        }).toList());
        vo.setSectionColumns(sections.stream().map(section -> {
            PermissionMatrixVO.SectionColumn column = new PermissionMatrixVO.SectionColumn();
            column.setSectionId(section.getId());
            column.setSectionCode(section.getSectionCode());
            column.setSectionName(section.getSectionName());
            return column;
        }).toList());
        vo.setPermissions(groupPermissionEntries(permissions).stream().map(permission -> {
            PermissionMatrixVO.PermissionEntry entry = new PermissionMatrixVO.PermissionEntry();
            entry.setRoleId(permission.roleId);
            entry.setSectionId(permission.sectionId);
            entry.setPermissionCodes(permission.permissionCodes);
            return entry;
        }).toList());
        return vo;
    }

    private ProjectRoleVO toRoleVO(ProjectRole role) {
        ProjectRoleVO vo = new ProjectRoleVO();
        vo.setId(role.getId());
        vo.setProjectId(role.getProjectId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setResponsibility(role.getResponsibility());
        vo.setMaxCount(role.getMaxCount());
        vo.setCurrentCount(role.getCurrentCount());
        vo.setRemainingCount(remainingCount(role));
        vo.setRequiredFlag(role.getRequiredFlag());
        vo.setSortOrder(role.getSortOrder());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }

    private SectionVO toSectionVO(ProjectSection section, Map<Long, ProjectRole> roleById) {
        SectionVO vo = new SectionVO();
        vo.setId(section.getId());
        vo.setProjectId(section.getProjectId());
        vo.setSectionCode(section.getSectionCode());
        vo.setSectionName(section.getSectionName());
        vo.setDescription(section.getDescription());
        vo.setRequiredFlag(section.getRequiredFlag());
        vo.setOwnerRoleId(section.getOwnerRoleId());
        ProjectRole ownerRole = section.getOwnerRoleId() == null ? null : roleById.get(section.getOwnerRoleId());
        vo.setOwnerRoleName(ownerRole == null ? "" : ownerRole.getRoleName());
        vo.setStatus(section.getStatus());
        vo.setSortOrder(section.getSortOrder());
        vo.setMissing("EMPTY".equalsIgnoreCase(section.getStatus()));
        vo.setCreateTime(section.getCreateTime());
        vo.setUpdateTime(section.getUpdateTime());
        return vo;
    }

    private RoleSpec fromRoleDTO(RoleHeadcountDTO dto) {
        RoleSpec spec = new RoleSpec();
        spec.roleCode = dto.getRoleCode();
        spec.roleName = dto.getRoleName();
        spec.responsibility = dto.getResponsibility();
        spec.maxCount = dto.getMaxCount();
        spec.requiredFlag = dto.getRequiredFlag();
        spec.sortOrder = dto.getSortOrder();
        return spec;
    }

    private List<RoleSpec> parseRoleDefaults(String json) {
        List<RoleSpec> specs = new ArrayList<>();
        JsonNode root = parseJson(json);
        if (root == null || !root.isArray()) {
            return specs;
        }
        for (JsonNode node : root) {
            RoleSpec spec = new RoleSpec();
            spec.roleCode = text(node, "code", "roleCode");
            spec.roleName = text(node, "name", "roleName");
            spec.responsibility = text(node, "responsibility", "description");
            spec.maxCount = integer(node, "maxCount", "max_count");
            spec.requiredFlag = bool(node, "required", "requiredFlag", "required_flag");
            spec.sortOrder = integer(node, "sortOrder", "sort_order");
            specs.add(spec);
        }
        return specs;
    }

    private List<SectionSpec> parseSectionDefaults(String json) {
        List<SectionSpec> specs = new ArrayList<>();
        JsonNode root = parseJson(json);
        if (root == null || !root.isArray()) {
            return specs;
        }
        for (JsonNode node : root) {
            SectionSpec spec = new SectionSpec();
            spec.sectionCode = text(node, "code", "sectionCode");
            spec.sectionName = text(node, "name", "sectionName");
            spec.description = text(node, "description");
            spec.requiredFlag = bool(node, "required", "requiredFlag", "required_flag");
            spec.ownerRoleCode = text(node, "ownerRole", "ownerRoleCode", "owner_role", "owner_role_code");
            spec.sortOrder = integer(node, "sortOrder", "sort_order");
            if (StringUtils.hasText(spec.sectionCode)) {
                specs.add(spec);
            }
        }
        return specs;
    }

    private Map<String, List<String>> parsePermissionDefaults(String json) {
        Map<String, List<String>> permissions = new LinkedHashMap<>();
        JsonNode root = parseJson(json);
        if (root == null || !root.isObject()) {
            return permissions;
        }
        root.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isArray()) {
                return;
            }
            List<String> codes = new ArrayList<>();
            entry.getValue().forEach(node -> {
                if (node.isTextual() && StringUtils.hasText(node.asText())) {
                    codes.add(normalize(node.asText()));
                }
            });
            permissions.put(normalize(entry.getKey()), codes);
        });
        return permissions;
    }

    private JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new BizException(400, "项目模板默认配置 JSON 不合法");
        }
    }

    private List<String> fallbackPermissions(ProjectRole role, ProjectSection section) {
        if (PROJECT_MANAGER.equalsIgnoreCase(role.getRoleCode())) {
            return ALL_PERMISSIONS;
        }
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (section.getRequiredFlag() != null && section.getRequiredFlag() == 1) {
            permissions.add("VIEW");
        }
        if (Objects.equals(section.getOwnerRoleId(), role.getId())) {
            permissions.addAll(List.of("VIEW", "EDIT", "COMMENT", "AI_GENERATE"));
        }
        return new ArrayList<>(permissions);
    }

    private List<String> distinctPermissions(List<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private String generateProjectCode() {
        String date = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 10; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
            String code = "TF-" + date + "-" + suffix;
            Long count = projectMapper.selectCount(new LambdaQueryWrapper<Project>().eq(Project::getProjectCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BizException(500, "项目编码生成失败");
    }

    private boolean isAdmin(SysUser user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private int toInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private Integer remainingCount(ProjectRole role) {
        if (role.getMaxCount() == null || role.getMaxCount() <= 0) {
            return null;
        }
        int currentCount = role.getCurrentCount() == null ? 0 : role.getCurrentCount();
        return Math.max(role.getMaxCount() - currentCount, 0);
    }

    private List<PermissionCell> groupPermissionEntries(List<RoleSectionPermission> permissions) {
        Map<String, PermissionCell> cells = new LinkedHashMap<>();
        for (RoleSectionPermission permission : permissions) {
            String key = permission.getProjectRoleId() + ":" + permission.getSectionId();
            PermissionCell cell = cells.computeIfAbsent(key, ignored -> {
                PermissionCell created = new PermissionCell();
                created.roleId = permission.getProjectRoleId();
                created.sectionId = permission.getSectionId();
                created.permissionCodes = new ArrayList<>();
                return created;
            });
            if (StringUtils.hasText(permission.getPermissionCode())) {
                cell.permissionCodes.add(permission.getPermissionCode());
            }
        }
        return new ArrayList<>(cells.values());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isNumber()) {
                return value.asInt();
            }
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                try {
                    return Integer.parseInt(value.asText());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Boolean bool(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isBoolean()) {
                return value.asBoolean();
            }
            if (value != null && value.isNumber()) {
                return value.asInt() != 0;
            }
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return Boolean.parseBoolean(value.asText());
            }
        }
        return null;
    }

    private static class RoleSpec {
        private String roleCode;
        private String roleName;
        private String responsibility;
        private Integer maxCount;
        private Boolean requiredFlag;
        private Integer sortOrder;
    }

    private static class SectionSpec {
        private String sectionCode;
        private String sectionName;
        private String description;
        private Boolean requiredFlag;
        private String ownerRoleCode;
        private Integer sortOrder;
    }

    private static class PermissionCell {
        private Long roleId;
        private Long sectionId;
        private List<String> permissionCodes;
    }
}
