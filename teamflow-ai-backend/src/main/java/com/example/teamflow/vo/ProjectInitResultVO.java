package com.example.teamflow.vo;

import lombok.Data;
import java.util.List;

@Data
public class ProjectInitResultVO {
    private ProjectVO project;
    private String projectCode;
    private String joinLink;
    private List<ProjectRoleVO> roles;
    private List<SectionVO> sections;
    private PermissionMatrixVO permissionMatrix;
}
