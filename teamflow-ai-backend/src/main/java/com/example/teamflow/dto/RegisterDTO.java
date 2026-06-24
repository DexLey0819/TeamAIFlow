package com.example.teamflow.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "真实姓名不能为空")
    private String realName;
    private String email;
    private String phone;
    private String role;
}
