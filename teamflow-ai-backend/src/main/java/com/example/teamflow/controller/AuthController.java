package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.dto.LoginDTO;
import com.example.teamflow.dto.ProfileUpdateDTO;
import com.example.teamflow.dto.RegisterDTO;
import com.example.teamflow.service.AuthService;
import com.example.teamflow.vo.LoginVO;
import com.example.teamflow.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(authService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.me());
    }

    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody ProfileUpdateDTO dto) {
        return Result.success(authService.updateMe(dto));
    }
}
