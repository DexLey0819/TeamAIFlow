package com.example.teamflow.vo;

import lombok.Data;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String role;
    private Integer status;
}
