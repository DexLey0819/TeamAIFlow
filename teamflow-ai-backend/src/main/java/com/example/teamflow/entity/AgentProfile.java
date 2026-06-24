package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_profile")
public class AgentProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scopeType;
    private Long scopeId;
    private Long projectId;
    private Long projectRoleId;
    private String roleCode;
    private String roleName;
    private String responsibilities;
    private String contextScope;
    private String outputTemplate;
    private String toolPermissions;
    private String memoryPolicy;
    private String systemPrompt;
    private String taskPromptTemplate;
    private String multimodalConfig;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
