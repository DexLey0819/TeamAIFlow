package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectInitDTO {
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;
    @NotBlank(message = "项目名称不能为空")
    private String projectName;
    private String description;
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
    private Integer maxMembers;
    private Boolean allowJoinApply;
    @Valid
    private List<RoleHeadcountDTO> roles;
    @Valid
    private ReportRuleDTO reportRule;
}
