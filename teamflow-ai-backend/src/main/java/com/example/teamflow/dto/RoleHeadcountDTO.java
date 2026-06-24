package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RoleHeadcountDTO {
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    private String responsibility;
    private Integer maxCount;
    private Boolean requiredFlag;
    private Integer sortOrder;
}
