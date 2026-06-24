package com.example.teamflow.service.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class EffectiveAgentProfile {
    private Long id;
    private Long projectId;
    private Long projectRoleId;
    private String roleCode;
    private String roleName;
    private String responsibilities;
    private List<String> contextScope;
    private String outputTemplate;
    private List<String> toolPermissions;
    private Map<String, Object> memoryPolicy;
    private String systemPrompt;
    private String taskPromptTemplate;
    private Map<String, Object> multimodalConfig;
    private boolean enabled;
    private String source;
    private LocalDateTime updateTime;
}
