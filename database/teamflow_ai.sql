CREATE DATABASE IF NOT EXISTS teamflow_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE teamflow_ai;

DROP TABLE IF EXISTS export_record;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS risk_snapshot;
DROP TABLE IF EXISTS ai_record;
DROP TABLE IF EXISTS project_file;
DROP TABLE IF EXISTS progress_record;
DROP TABLE IF EXISTS progress_report_rule;
DROP TABLE IF EXISTS task_log;
DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS section_review;
DROP TABLE IF EXISTS section_comment;
DROP TABLE IF EXISTS role_section_permission;
DROP TABLE IF EXISTS section_content;
DROP TABLE IF EXISTS project_section;
DROP TABLE IF EXISTS project_join_apply;
DROP TABLE IF EXISTS project_member;
DROP TABLE IF EXISTS agent_profile;
DROP TABLE IF EXISTS project_role;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS project_template;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(30),
    role VARCHAR(20) NOT NULL COMMENT 'USER, ADMIN',
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (role IN ('USER', 'ADMIN')),
    INDEX idx_sys_user_role (role),
    INDEX idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    mode VARCHAR(50) NOT NULL COMMENT 'FULL_STACK, FRONT_BACKEND, SOFTWARE_ENGINEERING, AI_APP, MOBILE_APP',
    description TEXT,
    role_defaults TEXT,
    section_defaults TEXT,
    permission_defaults TEXT,
    ai_check_rules TEXT,
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (mode IN ('FULL_STACK', 'FRONT_BACKEND', 'SOFTWARE_ENGINEERING', 'AI_APP', 'MOBILE_APP')),
    INDEX idx_template_mode (mode),
    INDEX idx_template_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    template_mode VARCHAR(50) NOT NULL,
    project_code VARCHAR(50) NOT NULL UNIQUE,
    project_name VARCHAR(120) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PLANNING' COMMENT 'PLANNING, DEVELOPING, TESTING, FINISHED, ARCHIVED',
    join_policy VARCHAR(30) DEFAULT 'REVIEW',
    join_link VARCHAR(255),
    archived_flag TINYINT DEFAULT 0,
    start_date DATE,
    end_date DATE,
    creator_id BIGINT NOT NULL,
    wbs_data LONGTEXT,
    github_repo VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (status IN ('PLANNING', 'DEVELOPING', 'TESTING', 'FINISHED', 'ARCHIVED')),
    CHECK (template_mode IN ('FULL_STACK', 'FRONT_BACKEND', 'SOFTWARE_ENGINEERING', 'AI_APP', 'MOBILE_APP')),
    INDEX idx_project_template (template_id),
    INDEX idx_project_creator (creator_id),
    INDEX idx_project_status (status),
    INDEX idx_project_mode (template_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(80) NOT NULL,
    responsibility TEXT,
    max_count INT DEFAULT 1,
    current_count INT DEFAULT 0,
    required_flag TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_role_code (project_id, role_code),
    INDEX idx_project_role_project (project_id),
    INDEX idx_project_role_required (required_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NULL,
    project_role_id BIGINT NULL,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(80) NOT NULL,
    responsibilities LONGTEXT NOT NULL,
    context_scope JSON NOT NULL,
    output_template LONGTEXT NOT NULL,
    tool_permissions JSON NOT NULL,
    memory_policy JSON NOT NULL,
    system_prompt LONGTEXT NOT NULL,
    task_prompt_template LONGTEXT NOT NULL,
    multimodal_config JSON NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_profile_scope_role (scope_type, scope_id, role_code),
    INDEX idx_agent_profile_project (project_id),
    INDEX idx_agent_profile_project_role (project_role_id),
    INDEX idx_agent_profile_role_code (role_code),
    CHECK (scope_type IN ('GLOBAL', 'PROJECT')),
    CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    project_role_id BIGINT NOT NULL,
    member_role VARCHAR(50) NOT NULL,
    member_title VARCHAR(80),
    join_source VARCHAR(30) DEFAULT 'INIT',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_user (project_id, user_id),
    INDEX idx_member_project (project_id),
    INDEX idx_member_user (user_id),
    INDEX idx_member_role (project_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_join_apply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    requested_role_id BIGINT NOT NULL,
    applicant_name VARCHAR(80) NOT NULL,
    applicant_email VARCHAR(100),
    apply_note TEXT,
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED',
    review_user_id BIGINT,
    review_comment TEXT,
    review_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    INDEX idx_apply_project (project_id),
    INDEX idx_apply_applicant (applicant_id),
    INDEX idx_apply_role (requested_role_id),
    INDEX idx_apply_status (status),
    INDEX idx_apply_reviewer (review_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_section (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    section_code VARCHAR(50) NOT NULL,
    section_name VARCHAR(100) NOT NULL,
    description TEXT,
    required_flag TINYINT DEFAULT 1,
    owner_role_id BIGINT,
    status VARCHAR(20) DEFAULT 'EMPTY' COMMENT 'EMPTY, DRAFT, REVIEWING, APPROVED, REJECTED',
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (status IN ('EMPTY', 'DRAFT', 'REVIEWING', 'APPROVED', 'REJECTED')),
    UNIQUE KEY uk_project_section_code (project_id, section_code),
    INDEX idx_section_project (project_id),
    INDEX idx_section_owner_role (owner_role_id),
    INDEX idx_section_status (status),
    INDEX idx_section_required (required_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE section_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    title VARCHAR(150) NOT NULL,
    body LONGTEXT,
    editor_id BIGINT NOT NULL,
    submit_status VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT, REVIEWING, APPROVED, REJECTED',
    submit_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (submit_status IN ('DRAFT', 'REVIEWING', 'APPROVED', 'REJECTED')),
    UNIQUE KEY uk_section_version (section_id, version_no),
    INDEX idx_content_section (section_id),
    INDEX idx_content_project (project_id),
    INDEX idx_content_editor (editor_id),
    INDEX idx_content_status (submit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_section_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    project_role_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    permission_code VARCHAR(30) NOT NULL COMMENT 'VIEW, EDIT, COMMENT, REVIEW, MANAGE, EXPORT, AI_GENERATE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_section_permission (project_role_id, section_id, permission_code),
    INDEX idx_permission_project (project_id),
    INDEX idx_permission_role (project_role_id),
    INDEX idx_permission_section (section_id),
    INDEX idx_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE section_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    content_id BIGINT,
    user_id BIGINT NOT NULL,
    comment_text TEXT NOT NULL,
    resolved_flag TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_comment_section (section_id),
    INDEX idx_comment_content (content_id),
    INDEX idx_comment_user (user_id),
    INDEX idx_comment_resolved (resolved_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE section_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    section_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    review_result VARCHAR(20) NOT NULL COMMENT 'APPROVED, REJECTED',
    review_comment TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (review_result IN ('APPROVED', 'REJECTED')),
    INDEX idx_review_section (section_id),
    INDEX idx_review_content (content_id),
    INDEX idx_review_reviewer (reviewer_id),
    INDEX idx_review_result (review_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    section_id BIGINT,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    assignee_id BIGINT,
    creator_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'TODO' COMMENT 'TODO, IN_PROGRESS, REVIEW, DONE, BLOCKED',
    priority VARCHAR(20) DEFAULT 'MEDIUM' COMMENT 'LOW, MEDIUM, HIGH, URGENT',
    start_date DATE,
    due_date DATE,
    finish_time DATETIME,
    block_reason TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (status IN ('TODO', 'IN_PROGRESS', 'REVIEW', 'DONE', 'BLOCKED')),
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    INDEX idx_task_project (project_id),
    INDEX idx_task_section (section_id),
    INDEX idx_task_assignee (assignee_id),
    INDEX idx_task_creator (creator_id),
    INDEX idx_task_status (status),
    INDEX idx_task_priority (priority),
    INDEX idx_task_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    old_value TEXT,
    new_value TEXT,
    content TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_task (task_id),
    INDEX idx_log_user (user_id),
    INDEX idx_log_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE progress_report_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    frequency VARCHAR(20) DEFAULT 'WEEKLY',
    report_day VARCHAR(20) DEFAULT 'SUNDAY',
    report_time VARCHAR(20) DEFAULT '22:00',
    required_flag TINYINT DEFAULT 1,
    overdue_policy TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report_rule_project (project_id),
    INDEX idx_report_rule_required (required_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE progress_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    project_role_id BIGINT,
    related_task_id BIGINT,
    related_section_id BIGINT,
    report_period VARCHAR(50),
    week_start DATE,
    week_end DATE,
    completed_work TEXT,
    problems TEXT,
    help_needed TEXT,
    next_plan TEXT,
    submit_status VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL, LATE, SUPPLEMENTED',
    submit_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (submit_status IN ('NORMAL', 'LATE', 'SUPPLEMENTED')),
    INDEX idx_progress_project (project_id),
    INDEX idx_progress_user (user_id),
    INDEX idx_progress_role (project_role_id),
    INDEX idx_progress_task (related_task_id),
    INDEX idx_progress_section (related_section_id),
    INDEX idx_progress_period (report_period),
    INDEX idx_progress_status (submit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    section_id BIGINT,
    uploader_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100),
    file_url VARCHAR(500) NOT NULL,
    file_size BIGINT,
    document_type VARCHAR(50),
    description TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_file_project (project_id),
    INDEX idx_file_section (section_id),
    INDEX idx_file_uploader (uploader_id),
    INDEX idx_file_document_type (document_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL COMMENT 'WEEKLY_REPORT, RISK_ANALYSIS, DOC_CHECK, SUMMARY_REPORT, SECTION_GENERATE',
    prompt LONGTEXT,
    result LONGTEXT,
    status VARCHAR(20) DEFAULT 'GENERATED',
    confirmed_flag TINYINT DEFAULT 0,
    source VARCHAR(30) NOT NULL DEFAULT 'ZHIPU_GLM' COMMENT 'ZHIPU_GLM, LOCAL_FALLBACK',
    model_name VARCHAR(80),
    risk_level VARCHAR(20),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (type IN ('WEEKLY_REPORT', 'RISK_ANALYSIS', 'DOC_CHECK', 'SUMMARY_REPORT', 'SECTION_GENERATE')),
    CHECK (status IN ('GENERATED', 'FAILED', 'DISCARDED')),
    CHECK (source IN ('ZHIPU_GLM', 'LOCAL_FALLBACK')),
    INDEX idx_ai_project (project_id),
    INDEX idx_ai_user (user_id),
    INDEX idx_ai_type (type),
    INDEX idx_ai_source (source),
    INDEX idx_ai_confirmed (confirmed_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE risk_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    ai_record_id BIGINT,
    risk_level VARCHAR(20) NOT NULL,
    risk_score DECIMAL(5,2) DEFAULT 0,
    task_delay_count INT DEFAULT 0,
    blocked_task_count INT DEFAULT 0,
    missing_report_count INT DEFAULT 0,
    rejected_section_count INT DEFAULT 0,
    trend VARCHAR(20) DEFAULT 'STABLE',
    summary TEXT,
    snapshot_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CHECK (trend IN ('UP', 'DOWN', 'STABLE')),
    INDEX idx_risk_project (project_id),
    INDEX idx_risk_ai_record (ai_record_id),
    INDEX idx_risk_level (risk_level),
    INDEX idx_risk_snapshot_time (snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT,
    read_flag TINYINT DEFAULT 0 COMMENT '0, 1',
    read_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (type IN ('JOIN_APPLICATION', 'APPLICATION_RESULT', 'SECTION_REVIEW', 'SECTION_COMMENT', 'TASK_ASSIGNMENT', 'TASK_BLOCKED', 'REPORT_OVERDUE', 'AI_RISK', 'EXPORT_COMPLETE')),
    CHECK (read_flag IN (0, 1)),
    INDEX idx_notification_user (user_id),
    INDEX idx_notification_project (project_id),
    INDEX idx_notification_type (type),
    INDEX idx_notification_read (read_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE export_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    export_scope VARCHAR(50) DEFAULT 'FULL_PROJECT',
    file_name VARCHAR(255),
    file_url VARCHAR(500),
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'GENERATED' COMMENT 'GENERATED, FAILED',
    failure_reason TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (status IN ('GENERATED', 'FAILED')),
    INDEX idx_export_project (project_id),
    INDEX idx_export_creator (creator_id),
    INDEX idx_export_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_user (id, username, password, real_name, email, phone, role, status) VALUES
(1, 'pm', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '张经理', 'pm@example.com', '13800000001', 'USER', 1),
(2, 'pd', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '陈产品', 'pd@example.com', '13800000002', 'USER', 1),
(3, 'frontend', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '李前端', 'frontend@example.com', '13800000003', 'USER', 1),
(4, 'backend', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '王后端', 'backend@example.com', '13800000004', 'USER', 1),
(5, 'qa', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '孙测试', 'qa@example.com', '13800000005', 'USER', 1),
(6, 'newmember', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '周申请', 'newmember@example.com', '13800000006', 'USER', 1),
(8, 'admin', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '系统管理员', 'admin@example.com', '13800000008', 'ADMIN', 1),
(9, 'applicant', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '赵申请', 'applicant@example.com', '13800000009', 'USER', 1),
(10, 'td', '$2y$10$4KgAL.9fyaQgwngtt9i9Y.OvDxKnFSca7hiobRHxJRBT6Nz/t19se', '杜总监', 'td@example.com', '13800000010', 'USER', 1);

INSERT INTO project_template (id, code, name, mode, description, role_defaults, section_defaults, permission_defaults, ai_check_rules, enabled) VALUES
(2, 'FRONT_BACKEND', '前后端分离项目模板', 'FRONT_BACKEND', '适合产品、前端、后端、测试岗位清晰分工的 Web 项目。',
 '[{"code":"PROJECT_MANAGER","name":"项目经理","maxCount":1},{"code":"PRODUCT_MANAGER","name":"产品经理","maxCount":1},{"code":"TECHNICAL_DIRECTOR","name":"技术总监","maxCount":1},{"code":"FRONTEND_DEV","name":"前端开发","maxCount":2},{"code":"BACKEND_DEV","name":"后端开发","maxCount":2},{"code":"QA","name":"测试负责人","maxCount":1}]',
 '[{"code":"MANAGEMENT_DELIVERY","name":"项目管理","required":true},{"code":"REQUIREMENT","name":"需求分析","required":true},{"code":"USE_CASE_ANALYSIS","name":"用例分析","required":true},{"code":"PROTOTYPE","name":"原型图设计","required":true},{"code":"TECH_FRAMEWORK","name":"技术框架设计","required":true},{"code":"DATABASE","name":"数据库设计","required":true},{"code":"USE_CASE","name":"用例设计","required":true},{"code":"API","name":"接口文档","required":true},{"code":"FRONTEND","name":"前端实现","required":true},{"code":"BACKEND","name":"后端实现","required":true},{"code":"TESTING","name":"测试与缺陷","required":true}]',
 '{"PROJECT_MANAGER":["VIEW","COMMENT","EXPORT"],"PRODUCT_MANAGER":["VIEW","COMMENT"],"TECHNICAL_DIRECTOR":["VIEW","COMMENT"],"FRONTEND_DEV":["VIEW","COMMENT"],"BACKEND_DEV":["VIEW","COMMENT"],"QA":["VIEW","COMMENT"]}',
 '{"provider":"zhipu","model":"glm-4.7","checks":["weekly_report","risk_analysis","doc_check","summary_report"]}', 1);

INSERT INTO project (id, template_id, template_mode, project_code, project_name, description, status, join_policy, join_link, archived_flag, start_date, end_date, creator_id) VALUES
(1, 2, 'FRONT_BACKEND', 'TF-DEMO-001', 'TeamFlowAI 智能项目小组协作系统', '基于前后端分离模式构建的智能软件项目协作平台，覆盖项目初始化、成员申请、章节协作、任务进度、AI 风险分析和成果导出。', 'DEVELOPING', 'REVIEW', '/join/TF-DEMO-001', 0, '2026-05-01', '2026-06-30', 1);

INSERT INTO project_role (id, project_id, role_code, role_name, responsibility, max_count, current_count, required_flag, sort_order) VALUES
(1, 1, 'PROJECT_MANAGER', '项目经理', '负责项目计划、成员申请审核、任务分派、风险处理和成果导出。', 1, 1, 1, 1),
(2, 1, 'PRODUCT_MANAGER', '产品经理', '负责需求分析、用例设计、原型设计和业务验收说明。', 1, 1, 1, 2),
(6, 1, 'TECHNICAL_DIRECTOR', '技术总监', '负责用例设计审核、数据库设计、接口设计、开发实现审核和测试审核。', 1, 1, 1, 3),
(3, 1, 'FRONTEND_DEV', '前端开发', '负责 Vue 前端页面、路由状态、接口联调和交互实现。', 2, 1, 1, 4),
(4, 1, 'BACKEND_DEV', '后端开发', '负责 Spring Boot 接口、数据库、权限和业务服务实现。', 2, 1, 1, 5),
(5, 1, 'QA', '测试负责人', '负责测试用例、缺陷跟踪、回归验证和质量报告。', 1, 1, 1, 6);

INSERT INTO agent_profile (
    scope_type, scope_id, project_id, project_role_id, role_code, role_name,
    responsibilities, context_scope, output_template, tool_permissions,
    memory_policy, system_prompt, task_prompt_template, multimodal_config, enabled
) VALUES
('GLOBAL', 0, NULL, NULL, 'PROJECT_MANAGER', '项目经理',
 '负责项目计划、成员申请审核、任务分派、风险处理和成果导出。',
 JSON_ARRAY('PROJECT_OVERVIEW', 'CURRENT_SECTION', 'PREVIOUS_SECTIONS', 'MEMBERS', 'TASKS', 'RISKS'),
 '按“目标与状态、关键行动、风险与依赖、责任人与截止时间”组织输出，结论明确，行动项可追踪。',
 JSON_ARRAY('READ_CONTEXT', 'GENERATE_DRAFT', 'SUGGEST_REVIEW_COMMENT', 'SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是项目经理智能体。聚焦计划协调、风险闭环和交付质量，给出可执行且可追踪的建议。',
 '你是${roleName}，正在参与项目“${projectName}”的“${sectionName}”。职责：${responsibilities}。可用上下文：${context}。请遵循输出要求：${outputTemplate}。仅使用允许的工具：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'PRODUCT_MANAGER', '产品经理',
 '负责需求分析、用例设计、原型设计和业务验收说明。',
 JSON_ARRAY('PROJECT_OVERVIEW', 'CURRENT_SECTION', 'PREVIOUS_SECTIONS', 'COMMENTS', 'REVIEW_HISTORY'),
 '按“用户目标、需求与范围、核心流程、验收标准、待确认事项”组织输出，保持业务语言清晰一致。',
 JSON_ARRAY('READ_CONTEXT', 'GENERATE_DRAFT', 'SUGGEST_REVIEW_COMMENT'),
 JSON_OBJECT('enabled', false),
 '你是产品经理智能体。聚焦用户价值、需求边界、业务流程和可验证的验收标准。',
 '你是${roleName}，正在参与项目“${projectName}”的“${sectionName}”。职责：${responsibilities}。可用上下文：${context}。请遵循输出要求：${outputTemplate}。仅使用允许的工具：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'TECHNICAL_DIRECTOR', '技术总监',
 '负责用例设计审核、数据库设计、接口设计、开发实现审核和测试审核。',
 JSON_ARRAY('PROJECT_OVERVIEW', 'CURRENT_SECTION', 'PREVIOUS_SECTIONS', 'TASKS', 'RISKS', 'FILES', 'REVIEW_HISTORY'),
 '按“技术结论、架构与数据设计、接口约束、实现风险、审核意见”组织输出，明确取舍和整改项。',
 JSON_ARRAY('READ_CONTEXT', 'GENERATE_DRAFT', 'SUGGEST_REVIEW_COMMENT', 'INSPECT_FILES', 'SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是技术总监智能体。聚焦架构一致性、技术可行性、实现质量和跨模块风险。',
 '你是${roleName}，正在参与项目“${projectName}”的“${sectionName}”。职责：${responsibilities}。可用上下文：${context}。请遵循输出要求：${outputTemplate}。仅使用允许的工具：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'FRONTEND_DEV', '前端开发',
 '负责 Vue 前端页面、路由状态、接口联调和交互实现。',
 JSON_ARRAY('PROJECT_OVERVIEW', 'CURRENT_SECTION', 'PREVIOUS_SECTIONS', 'TASKS', 'FILES'),
 '按“页面与交互、组件与状态、接口联调、异常处理、验证结果”组织输出，给出可落地的前端实现说明。',
 JSON_ARRAY('READ_CONTEXT', 'GENERATE_DRAFT', 'INSPECT_FILES', 'SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是前端开发智能体。聚焦 Vue 页面、交互状态、接口联调、可用性和前端工程质量。',
 '你是${roleName}，正在参与项目“${projectName}”的“${sectionName}”。职责：${responsibilities}。可用上下文：${context}。请遵循输出要求：${outputTemplate}。仅使用允许的工具：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'BACKEND_DEV', '后端开发',
 '负责 Spring Boot 接口、数据库、权限和业务服务实现。',
 JSON_ARRAY('PROJECT_OVERVIEW', 'CURRENT_SECTION', 'PREVIOUS_SECTIONS', 'TASKS', 'RISKS', 'FILES'),
 '按“接口契约、数据与事务、权限校验、业务实现、异常与验证”组织输出，说明关键边界和实现依据。',
 JSON_ARRAY('READ_CONTEXT', 'GENERATE_DRAFT', 'INSPECT_FILES', 'SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是后端开发智能体。聚焦 Spring Boot 服务、数据一致性、权限边界、接口契约和可靠性。',
 '你是${roleName}，正在参与项目“${projectName}”的“${sectionName}”。职责：${responsibilities}。可用上下文：${context}。请遵循输出要求：${outputTemplate}。仅使用允许的工具：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1),
('GLOBAL', 0, NULL, NULL, 'QA', '测试负责人',
 '负责测试用例、缺陷跟踪、回归验证和质量报告。',
 JSON_ARRAY('PROJECT_OVERVIEW', 'CURRENT_SECTION', 'PREVIOUS_SECTIONS', 'TASKS', 'RISKS', 'COMMENTS', 'REVIEW_HISTORY'),
 '按“测试范围、用例与结果、缺陷与影响、回归状态、质量结论”组织输出，确保结论可复现可追踪。',
 JSON_ARRAY('READ_CONTEXT', 'GENERATE_DRAFT', 'SUGGEST_REVIEW_COMMENT', 'SUGGEST_TASKS'),
 JSON_OBJECT('enabled', false),
 '你是测试负责人智能体。聚焦覆盖范围、缺陷证据、回归结果和可追踪的质量结论。',
 '你是${roleName}，正在参与项目“${projectName}”的“${sectionName}”。职责：${responsibilities}。可用上下文：${context}。请遵循输出要求：${outputTemplate}。仅使用允许的工具：${toolPermissions}。',
 JSON_OBJECT('enabled', false, 'inputTypes', JSON_ARRAY()), 1);

INSERT INTO project_member (id, project_id, user_id, project_role_id, member_role, member_title, join_source) VALUES
(1, 1, 1, 1, 'PROJECT_MANAGER', '项目经理', 'INIT'),
(2, 1, 2, 2, 'PRODUCT_MANAGER', '产品经理', 'INIT'),
(3, 1, 3, 3, 'FRONTEND_DEV', '前端开发', 'INIT'),
(4, 1, 4, 4, 'BACKEND_DEV', '后端开发', 'INIT'),
(5, 1, 5, 5, 'QA', '测试负责人', 'INIT'),
(6, 1, 10, 6, 'TECHNICAL_DIRECTOR', '技术总监', 'INIT');

INSERT INTO project_join_apply (id, project_id, applicant_id, requested_role_id, applicant_name, applicant_email, apply_note, status, review_user_id, review_comment, review_time) VALUES
(1, 1, 6, 3, '周申请', 'newmember@example.com', '希望加入前端岗位，协助完成统计图表和通知中心页面。', 'PENDING', NULL, NULL, NULL);

INSERT INTO project_section (id, project_id, section_code, section_name, description, required_flag, owner_role_id, status, sort_order) VALUES
(1, 1, 'MANAGEMENT_DELIVERY', '项目管理', '记录项目 WBS 计划、甘特图和资源负载。', 1, 1, 'APPROVED', 1),
(2, 1, 'REQUIREMENT', '需求分析', '记录项目背景、目标用户、业务流程和功能需求。', 1, 2, 'APPROVED', 2),
(3, 1, 'USE_CASE_ANALYSIS', '用例分析', '记录系统核心业务用例分析和用户角色交互流。', 1, 2, 'APPROVED', 3),
(4, 1, 'PROTOTYPE', '原型图设计', '记录低保真页面、关键交互和页面跳转说明。', 1, 2, 'REVIEWING', 4),
(5, 1, 'TECH_FRAMEWORK', '技术框架设计', '记录项目所采用的整体技术架构、基础框架及脚手架设计。', 1, 6, 'APPROVED', 5),
(6, 1, 'DATABASE', '数据库设计', '记录数据对象、表结构、索引和初始化数据。', 1, 6, 'APPROVED', 6),
(7, 1, 'USE_CASE', '用例设计', '记录核心用例、参与者和主要交互流程。', 1, 6, 'APPROVED', 7),
(8, 1, 'API', '接口文档', '记录 REST API 分组、请求响应字段和权限约束。', 1, 6, 'DRAFT', 8),
(9, 1, 'FRONTEND', '前端实现', '记录前端工程结构、页面实现和接口联调状态。', 1, 3, 'DRAFT', 9),
(10, 1, 'BACKEND', '后端实现', '记录后端模块、服务逻辑、鉴权和异常处理实现。', 1, 4, 'DRAFT', 10),
(11, 1, 'TESTING', '测试与缺陷', '记录测试计划、测试用例、缺陷清单和回归结果。', 1, 5, 'EMPTY', 11);

INSERT INTO section_content (id, section_id, project_id, version_no, title, body, editor_id, submit_status, submit_time) VALUES
(1, 1, 1, 1, '项目管理计划说明', '包含项目 WBS 任务分解和甘特图排程，确定了各个里程碑的交付时间。', 1, 'APPROVED', '2026-05-05 10:00:00'),
(2, 2, 1, 1, 'TeamFlowAI 需求分析说明书', '目标是支持项目小组完成项目创建、成员协作、任务跟踪、进度汇报、AI 风险分析和成果导出。核心用户包括项目经理、团队成员和管理员。', 2, 'APPROVED', '2026-05-08 18:00:00'),
(3, 3, 1, 1, '核心用例分析', '用例分析涵盖了登录认证、成员审核、章节编辑、任务流转等核心业务场景。', 2, 'APPROVED', '2026-05-10 18:00:00'),
(4, 4, 1, 1, '低保真原型图设计', '已完成工作台、项目详情、成员申请、章节协作、任务看板、进度报告、AI 中心、统计和导出页面原型。', 2, 'REVIEWING', '2026-05-15 20:00:00'),
(5, 5, 1, 1, '技术框架与脚手架设计说明', '前端基于 Vue 3 + Vite，后端基于 Spring Boot 3 + MyBatis-Plus。', 10, 'APPROVED', '2026-05-11 11:00:00'),
(6, 6, 1, 1, '数据库设计文档', '数据库围绕模板、项目、角色、成员、申请、章节、任务、进度、AI 记录、风险快照、通知和导出记录设计。', 10, 'APPROVED', '2026-05-12 19:30:00'),
(7, 7, 1, 1, '系统用例详细设计', '核心用例包括登录注册、创建项目、申请加入项目、审核申请、维护章节、任务流转、提交周报、生成 AI 报告和导出成果。', 10, 'APPROVED', '2026-05-13 14:00:00'),
(8, 8, 1, 1, '接口文档草稿', '接口按认证、模板、项目初始化、申请审核、权限、章节、任务、进度、AI、统计、通知、导出和教师看板分组。', 10, 'DRAFT', NULL),
(9, 9, 1, 1, '前端实现记录', '前端使用 Vue 3、Vite、Element Plus、Pinia、Axios 和 ECharts，当前正在搭建项目空间导航和任务看板。', 3, 'DRAFT', NULL),
(10, 10, 1, 1, '后端实现记录', '后端使用 Spring Boot 3、Spring Security、JWT、MyBatis-Plus 和 MySQL 8，当前正在重建领域模型。', 4, 'DRAFT', NULL),
(11, 11, 1, 1, '测试与缺陷报告草稿', '当前测试用例规划覆盖率达 95% 以上，支持一键回归测试验证。', 5, 'DRAFT', NULL);

-- View and Comment permissions for everyone
INSERT INTO role_section_permission (project_id, project_role_id, section_id, permission_code) VALUES
-- Role 1 (Project Manager)
(1, 1, 1, 'VIEW'), (1, 1, 1, 'COMMENT'), (1, 1, 1, 'EXPORT'),
(1, 1, 2, 'VIEW'), (1, 1, 2, 'COMMENT'), (1, 1, 2, 'EXPORT'),
(1, 1, 3, 'VIEW'), (1, 1, 3, 'COMMENT'), (1, 1, 3, 'EXPORT'),
(1, 1, 4, 'VIEW'), (1, 1, 4, 'COMMENT'), (1, 1, 4, 'EXPORT'),
(1, 1, 5, 'VIEW'), (1, 1, 5, 'COMMENT'), (1, 1, 5, 'EXPORT'),
(1, 1, 6, 'VIEW'), (1, 1, 6, 'COMMENT'), (1, 1, 6, 'EXPORT'),
(1, 1, 7, 'VIEW'), (1, 1, 7, 'COMMENT'), (1, 1, 7, 'EXPORT'),
(1, 1, 8, 'VIEW'), (1, 1, 8, 'COMMENT'), (1, 1, 8, 'EXPORT'),
(1, 1, 9, 'VIEW'), (1, 1, 9, 'COMMENT'), (1, 1, 9, 'EXPORT'),
(1, 1, 10, 'VIEW'), (1, 1, 10, 'COMMENT'), (1, 1, 10, 'EXPORT'),
(1, 1, 11, 'VIEW'), (1, 1, 11, 'COMMENT'), (1, 1, 11, 'EXPORT'),
-- Role 2 (Product Manager)
(1, 2, 1, 'VIEW'), (1, 2, 1, 'COMMENT'), (1, 2, 2, 'VIEW'), (1, 2, 2, 'COMMENT'),
(1, 2, 3, 'VIEW'), (1, 2, 3, 'COMMENT'), (1, 2, 4, 'VIEW'), (1, 2, 4, 'COMMENT'),
(1, 2, 5, 'VIEW'), (1, 2, 5, 'COMMENT'), (1, 2, 6, 'VIEW'), (1, 2, 6, 'COMMENT'),
(1, 2, 7, 'VIEW'), (1, 2, 7, 'COMMENT'), (1, 2, 8, 'VIEW'), (1, 2, 8, 'COMMENT'),
(1, 2, 9, 'VIEW'), (1, 2, 9, 'COMMENT'), (1, 2, 10, 'VIEW'), (1, 2, 10, 'COMMENT'),
(1, 2, 11, 'VIEW'), (1, 2, 11, 'COMMENT'),
-- Role 3 (Frontend Dev)
(1, 3, 1, 'VIEW'), (1, 3, 1, 'COMMENT'), (1, 3, 2, 'VIEW'), (1, 3, 2, 'COMMENT'),
(1, 3, 3, 'VIEW'), (1, 3, 3, 'COMMENT'), (1, 3, 4, 'VIEW'), (1, 3, 4, 'COMMENT'),
(1, 3, 5, 'VIEW'), (1, 3, 5, 'COMMENT'), (1, 3, 6, 'VIEW'), (1, 3, 6, 'COMMENT'),
(1, 3, 7, 'VIEW'), (1, 3, 7, 'COMMENT'), (1, 3, 8, 'VIEW'), (1, 3, 8, 'COMMENT'),
(1, 3, 9, 'VIEW'), (1, 3, 9, 'COMMENT'), (1, 3, 10, 'VIEW'), (1, 3, 10, 'COMMENT'),
(1, 3, 11, 'VIEW'), (1, 3, 11, 'COMMENT'),
-- Role 4 (Backend Dev)
(1, 4, 1, 'VIEW'), (1, 4, 1, 'COMMENT'), (1, 4, 2, 'VIEW'), (1, 4, 2, 'COMMENT'),
(1, 4, 3, 'VIEW'), (1, 4, 3, 'COMMENT'), (1, 4, 4, 'VIEW'), (1, 4, 4, 'COMMENT'),
(1, 4, 5, 'VIEW'), (1, 4, 5, 'COMMENT'), (1, 4, 6, 'VIEW'), (1, 4, 6, 'COMMENT'),
(1, 4, 7, 'VIEW'), (1, 4, 7, 'COMMENT'), (1, 4, 8, 'VIEW'), (1, 4, 8, 'COMMENT'),
(1, 4, 9, 'VIEW'), (1, 4, 9, 'COMMENT'), (1, 4, 10, 'VIEW'), (1, 4, 10, 'COMMENT'),
(1, 4, 11, 'VIEW'), (1, 4, 11, 'COMMENT'),
-- Role 5 (QA)
(1, 5, 1, 'VIEW'), (1, 5, 1, 'COMMENT'), (1, 5, 2, 'VIEW'), (1, 5, 2, 'COMMENT'),
(1, 5, 3, 'VIEW'), (1, 5, 3, 'COMMENT'), (1, 5, 4, 'VIEW'), (1, 5, 4, 'COMMENT'),
(1, 5, 5, 'VIEW'), (1, 5, 5, 'COMMENT'), (1, 5, 6, 'VIEW'), (1, 5, 6, 'COMMENT'),
(1, 5, 7, 'VIEW'), (1, 5, 7, 'COMMENT'), (1, 5, 8, 'VIEW'), (1, 5, 8, 'COMMENT'),
(1, 5, 9, 'VIEW'), (1, 5, 9, 'COMMENT'), (1, 5, 10, 'VIEW'), (1, 5, 10, 'COMMENT'),
(1, 5, 11, 'VIEW'), (1, 5, 11, 'COMMENT'),
-- Role 6 (Technical Director)
(1, 6, 1, 'VIEW'), (1, 6, 1, 'COMMENT'), (1, 6, 2, 'VIEW'), (1, 6, 2, 'COMMENT'),
(1, 6, 3, 'VIEW'), (1, 6, 3, 'COMMENT'), (1, 6, 4, 'VIEW'), (1, 6, 4, 'COMMENT'),
(1, 6, 5, 'VIEW'), (1, 6, 5, 'COMMENT'), (1, 6, 6, 'VIEW'), (1, 6, 6, 'COMMENT'),
(1, 6, 7, 'VIEW'), (1, 6, 7, 'COMMENT'), (1, 6, 8, 'VIEW'), (1, 6, 8, 'COMMENT'),
(1, 6, 9, 'VIEW'), (1, 6, 9, 'COMMENT'), (1, 6, 10, 'VIEW'), (1, 6, 10, 'COMMENT'),
(1, 6, 11, 'VIEW'), (1, 6, 11, 'COMMENT');

-- Specific edits/reviews permissions
INSERT INTO role_section_permission (project_id, project_role_id, section_id, permission_code) VALUES
-- MANAGEMENT_DELIVERY (section 1): Project Manager edits, other roles review
(1, 1, 1, 'EDIT'), (1, 1, 1, 'AI_GENERATE'),
(1, 2, 1, 'REVIEW'), (1, 3, 1, 'REVIEW'), (1, 4, 1, 'REVIEW'), (1, 5, 1, 'REVIEW'), (1, 6, 1, 'REVIEW'),
-- REQUIREMENT (section 2): Product Manager edits, Project Manager reviews
(1, 2, 2, 'EDIT'), (1, 2, 2, 'AI_GENERATE'), (1, 1, 2, 'REVIEW'),
-- USE_CASE_ANALYSIS (section 3): Product Manager edits, Project Manager & Technical Director reviews
(1, 2, 3, 'EDIT'), (1, 2, 3, 'AI_GENERATE'), (1, 1, 3, 'REVIEW'), (1, 6, 3, 'REVIEW'),
-- PROTOTYPE (section 4): Product Manager edits, Project Manager reviews
(1, 2, 4, 'EDIT'), (1, 2, 4, 'AI_GENERATE'), (1, 1, 4, 'REVIEW'),
-- TECH_FRAMEWORK (section 5): Technical Director edits, Project Manager reviews
(1, 6, 5, 'EDIT'), (1, 6, 5, 'AI_GENERATE'), (1, 1, 5, 'REVIEW'),
-- DATABASE (section 6): Technical Director edits, Project Manager reviews
(1, 6, 6, 'EDIT'), (1, 6, 6, 'AI_GENERATE'), (1, 1, 6, 'REVIEW'),
-- USE_CASE (section 7): Technical Director edits, Project Manager reviews
(1, 6, 7, 'EDIT'), (1, 6, 7, 'AI_GENERATE'), (1, 1, 7, 'REVIEW'),
-- API (section 8): Technical Director edits, Project Manager reviews
(1, 6, 8, 'EDIT'), (1, 6, 8, 'AI_GENERATE'), (1, 1, 8, 'REVIEW'),
-- FRONTEND (section 9): Frontend Dev edits, Technical Director reviews
(1, 3, 9, 'EDIT'), (1, 3, 9, 'AI_GENERATE'), (1, 6, 9, 'REVIEW'),
-- BACKEND (section 10): Backend Dev edits, Technical Director reviews
(1, 4, 10, 'EDIT'), (1, 4, 10, 'AI_GENERATE'), (1, 6, 10, 'REVIEW'),
-- TESTING (section 11): QA edits, Technical Director and Project Manager reviews
(1, 5, 11, 'EDIT'), (1, 5, 11, 'AI_GENERATE'), (1, 6, 11, 'REVIEW'), (1, 1, 11, 'REVIEW');

INSERT INTO section_comment (id, section_id, content_id, user_id, comment_text, resolved_flag) VALUES
(1, 3, 3, 3, '原型页面需要补充通知中心的空状态和已读状态说明。', 0),
(2, 5, 5, 1, '接口设计请补充申请审核时角色人数上限校验。', 0),
(3, 4, 4, 10, '数据库对象覆盖完整，建议在风险快照上保留趋势字段。', 1);

INSERT INTO section_review (id, section_id, content_id, reviewer_id, review_result, review_comment) VALUES
(1, 1, 1, 1, 'APPROVED', '需求范围清晰，覆盖 P0 和 P1 功能。'),
(2, 2, 2, 1, 'APPROVED', '用例能支撑后续接口和页面设计。'),
(3, 4, 4, 10, 'APPROVED', '表结构完整，索引设计合理。');

INSERT INTO task (id, project_id, section_id, title, description, assignee_id, creator_id, status, priority, start_date, due_date, finish_time, block_reason) VALUES
(1, 1, 1, '完成需求分析章节', '梳理 P0 和 P1 范围，形成需求分析章节初稿并提交评审。', 2, 1, 'DONE', 'HIGH', '2026-05-01', '2026-05-08', '2026-05-08 18:00:00', NULL),
(2, 1, 3, '绘制低保真原型', '完成 27 个低保真页面的页面结构和跳转关系。', 2, 1, 'REVIEW', 'HIGH', '2026-05-09', '2026-05-15', NULL, NULL),
(3, 1, 4, '重建数据库初始化脚本', '按模板驱动模型重建 MySQL 8 初始化脚本和演示数据。', 4, 1, 'DONE', 'URGENT', '2026-05-05', '2026-05-12', '2026-05-12 19:30:00', NULL),
(4, 1, 5, '设计后端 API 契约', '输出认证、项目、申请、章节、任务、进度、AI、通知和导出接口清单。', 4, 1, 'IN_PROGRESS', 'HIGH', '2026-05-13', '2026-05-24', NULL, NULL),
(5, 1, 6, '搭建前端项目空间导航', '实现概览、成员申请、权限、章节、任务、进度、AI、统计和导出导航。', 3, 1, 'IN_PROGRESS', 'MEDIUM', '2026-05-16', '2026-05-26', NULL, NULL),
(6, 1, 8, '编写接口测试用例', '为项目初始化、申请审核、任务流转和进度报告编写测试用例。', 5, 1, 'TODO', 'MEDIUM', '2026-05-20', '2026-05-28', NULL, NULL),
(7, 1, 7, '排查后端权限校验阻塞', '章节评审和导出权限校验规则需要与角色权限矩阵对齐。', 4, 1, 'BLOCKED', 'HIGH', '2026-05-18', '2026-05-25', NULL, '角色权限矩阵尚未完成后端落库读取。');

INSERT INTO task_log (id, task_id, user_id, action_type, old_status, new_status, old_value, new_value, content) VALUES
(1, 1, 2, 'STATUS_CHANGE', 'REVIEW', 'DONE', NULL, NULL, '需求分析章节通过评审并归档。'),
(2, 2, 2, 'STATUS_CHANGE', 'IN_PROGRESS', 'REVIEW', NULL, NULL, '原型设计已提交项目经理评审。'),
(3, 3, 4, 'STATUS_CHANGE', 'IN_PROGRESS', 'DONE', NULL, NULL, '数据库初始化脚本已完成第一版。'),
(4, 4, 4, 'STATUS_CHANGE', 'TODO', 'IN_PROGRESS', NULL, NULL, '开始设计后端 API 契约。'),
(5, 7, 4, 'STATUS_CHANGE', 'IN_PROGRESS', 'BLOCKED', NULL, '权限矩阵读取未完成', '后端权限校验实现被权限矩阵读取逻辑阻塞。');

INSERT INTO progress_report_rule (id, project_id, frequency, report_day, report_time, required_flag, overdue_policy) VALUES
(1, 1, 'WEEKLY', 'SUNDAY', '22:00', 1, '每周日晚 22:00 前提交，逾期标记为 LATE；补交后标记为 SUPPLEMENTED。');

INSERT INTO progress_record (id, project_id, user_id, project_role_id, related_task_id, related_section_id, report_period, week_start, week_end, completed_work, problems, help_needed, next_plan, submit_status, submit_time) VALUES
(1, 1, 1, 1, NULL, 9, '2026-W21', '2026-05-18', '2026-05-24', '完成任务拆分、成员申请规则确认和风险跟踪。', '后端权限矩阵读取还未闭环。', '需要后端同学优先完成权限查询接口。', '推进 AI 风险分析和导出验收规则。', 'NORMAL', '2026-05-24 20:30:00'),
(2, 1, 2, 2, 2, 3, '2026-W21', '2026-05-18', '2026-05-24', '完成低保真原型和核心用例说明。', '通知中心空状态还需补充。', '请前端同学确认页面组件边界。', '根据评审意见补充原型说明。', 'NORMAL', '2026-05-24 21:00:00'),
(3, 1, 3, 3, 5, 6, '2026-W21', '2026-05-18', '2026-05-24', '完成前端项目空间导航和任务看板初稿。', '部分接口字段仍待后端确认。', '需要后端提供稳定 API 契约。', '接入章节列表和通知中心接口。', 'NORMAL', '2026-05-24 21:20:00'),
(4, 1, 4, 4, 4, 5, '2026-W21', '2026-05-18', '2026-05-24', '完成数据库模型和部分 API 契约。', '权限矩阵读取逻辑阻塞任务 7。', '需要项目经理确认权限码枚举。', '完成项目初始化、章节权限和申请审核接口。', 'LATE', '2026-05-25 09:15:00'),
(5, 1, 5, 5, 6, 8, '2026-W21', '2026-05-18', '2026-05-24', '整理测试范围和接口测试用例框架。', '测试数据依赖数据库新脚本。', '需要数据库脚本稳定后导入验证。', '补充申请审核和任务流转测试用例。', 'SUPPLEMENTED', '2026-05-25 11:00:00');

INSERT INTO project_file (id, project_id, section_id, uploader_id, file_name, file_type, file_url, file_size, document_type, description) VALUES
(1, 1, 1, 2, 'TeamFlowAI需求分析说明书.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', './upload/demo/teamflowai-requirement.docx', 32768, 'REQUIREMENT', '需求分析章节附件。'),
(2, 1, 3, 2, 'TeamFlowAI低保真原型.png', 'image/png', './upload/demo/teamflowai-prototype.png', 58321, 'PROTOTYPE', '原型设计截图。'),
(3, 1, 4, 4, 'teamflow_ai.sql', 'text/sql', './upload/demo/teamflow_ai.sql', 24576, 'DATABASE', '数据库初始化脚本。');

INSERT INTO ai_record (id, project_id, user_id, type, prompt, result, status, confirmed_flag, source, model_name, risk_level) VALUES
(1, 1, 1, 'WEEKLY_REPORT', '使用 glm-4.7 汇总项目本周进度；analyze only provided data; do not fabricate missing content。', '本周完成需求、用例、数据库章节，原型进入评审，前后端实现推进中。建议优先解除权限矩阵读取阻塞。', 'GENERATED', 1, 'ZHIPU_GLM', 'glm-4.7', 'MEDIUM'),
(2, 1, 1, 'RISK_ANALYSIS', '使用 glm-4.7 根据任务、章节和周报分析风险；analyze only provided data; do not fabricate missing content。', '风险等级：中。主要风险来自后端权限矩阵读取阻塞、测试用例尚未开始、接口字段仍待稳定。建议项目经理组织一次权限码和 API 契约对齐。', 'GENERATED', 1, 'ZHIPU_GLM', 'glm-4.7', 'MEDIUM'),
(3, 1, 2, 'DOC_CHECK', '检查章节完整性，模型 glm-4.7，仅基于已有章节状态判断。', '缺失项：测试与缺陷、项目总结为空；API、前端实现、后端实现仍为草稿；原型设计待评审。', 'GENERATED', 0, 'ZHIPU_GLM', 'glm-4.7', 'LOW'),
(4, 1, 1, 'SUMMARY_REPORT', '使用 glm-4.7 生成项目阶段总结；analyze only provided data; do not fabricate missing content。', '项目已建立完整分工，需求和数据库已通过评审，下一阶段应聚焦接口联调、测试闭环和导出准备。', 'GENERATED', 0, 'ZHIPU_GLM', 'glm-4.7', 'MEDIUM');

INSERT INTO risk_snapshot (id, project_id, ai_record_id, risk_level, risk_score, task_delay_count, blocked_task_count, missing_report_count, rejected_section_count, trend, summary, snapshot_time) VALUES
(1, 1, 2, 'MEDIUM', 62.50, 1, 1, 0, 0, 'UP', '权限矩阵读取阻塞导致后端实现和测试准备存在中等风险。', '2026-05-24 22:30:00'),
(2, 1, 2, 'MEDIUM', 58.00, 1, 1, 0, 0, 'STABLE', '风险仍集中在权限接口和测试用例启动，整体可控。', '2026-05-25 22:30:00');

INSERT INTO notification (id, user_id, project_id, type, title, content, read_flag, read_time) VALUES
(1, 1, 1, 'JOIN_APPLICATION', '新的加入申请待审核', '周申请希望以前端开发身份加入 TF-DEMO-001。', 0, NULL),
(2, 6, 1, 'APPLICATION_RESULT', '加入申请已提交', '你的加入申请已提交，等待项目经理审核。', 1, '2026-05-24 19:10:00'),
(3, 3, 1, 'TASK_ASSIGNMENT', '你有新的前端任务', '请继续完成前端项目空间导航和任务看板。', 0, NULL),
(4, 4, 1, 'TASK_BLOCKED', '任务阻塞提醒', '排查后端权限校验阻塞任务已标记为 BLOCKED。', 0, NULL),
(5, 1, 1, 'AI_RISK', 'AI 风险分析已生成', 'Zhipu GLM 已生成 TF-DEMO-001 的中风险分析。', 1, '2026-05-24 22:40:00'),
(6, 5, 1, 'REPORT_OVERDUE', '进度报告补交通知', '你的周报已补交，状态为 SUPPLEMENTED。', 1, '2026-05-25 11:05:00'),
(7, 1, 1, 'EXPORT_COMPLETE', '项目成果已导出', 'TF-DEMO-001 阶段成果包已生成。', 0, NULL);

INSERT INTO export_record (id, project_id, creator_id, export_scope, file_name, file_url, file_size, status, failure_reason) VALUES
(1, 1, 1, 'FULL_PROJECT', 'TF-DEMO-001-teamflowai-export.html', './exports/TF-DEMO-001-teamflowai-export.html', 65536, 'GENERATED', NULL);
