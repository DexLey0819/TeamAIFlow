package com.example.teamflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentProfileUpdateDTO {
    @NotBlank
    @Size(max = 4000)
    private String responsibilities;

    @NotNull
    @Size(min = 1, max = 9)
    private List<String> contextScope;

    @NotBlank
    @Size(max = 6000)
    private String outputTemplate;

    @NotNull
    @Size(max = 5)
    private List<String> toolPermissions;

    @NotNull
    private Map<String, Object> memoryPolicy;

    @NotBlank
    @Size(max = 12000)
    private String systemPrompt;

    @NotBlank
    @Size(max = 16000)
    private String taskPromptTemplate;

    @NotNull
    private Map<String, Object> multimodalConfig;

    @NotNull
    private Boolean enabled;
}
