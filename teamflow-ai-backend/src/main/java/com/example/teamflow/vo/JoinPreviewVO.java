package com.example.teamflow.vo;

import lombok.Data;
import java.util.List;

@Data
public class JoinPreviewVO {
    private ProjectVO project;
    private Boolean joinEnabled;
    private List<ProjectRoleVO> availableRoles;
}
