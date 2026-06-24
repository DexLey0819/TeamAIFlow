package com.example.teamflow.dto;

import lombok.Data;

@Data
public class TemplateUpdateDTO {
    private String name;
    private String description;
    private String roleDefaults;
    private String sectionDefaults;
    private String permissionDefaults;
    private String aiCheckRules;
    private Boolean enabled;
}
