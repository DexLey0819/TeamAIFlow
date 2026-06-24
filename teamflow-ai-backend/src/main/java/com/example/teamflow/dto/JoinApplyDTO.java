package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class JoinApplyDTO {
    @NotNull(message = "申请角色不能为空")
    private Long requestedRoleId;
    @NotBlank(message = "申请人姓名不能为空")
    private String applicantName;
    private String applicantEmail;
    private String applyNote;
}
