package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class JoinApplyVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long applicantId;
    private String applicantUsername;
    private String applicantName;
    private String applicantEmail;
    private Long requestedRoleId;
    private String requestedRoleName;
    private String applyNote;
    private String status;
    private Long reviewUserId;
    private String reviewUserName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
