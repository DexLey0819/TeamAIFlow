package com.example.teamflow.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class ProfileUpdateDTO {
    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
}
