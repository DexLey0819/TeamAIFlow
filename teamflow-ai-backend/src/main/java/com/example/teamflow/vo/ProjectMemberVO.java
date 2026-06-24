package com.example.teamflow.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProjectMemberVO {
    private Long id;
    private Long projectId;
    private Long userId;
    private String username;
    private String realName;
    private String email;
    private Long projectRoleId;
    private String memberRole;
    private String memberTitle;
    private String joinSource;
    private LocalDateTime joinTime;
}
