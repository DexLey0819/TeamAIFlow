package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.dto.LoginDTO;
import com.example.teamflow.dto.ProfileUpdateDTO;
import com.example.teamflow.dto.RegisterDTO;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.security.JwtUtil;
import com.example.teamflow.security.LoginUser;
import com.example.teamflow.vo.LoginVO;
import com.example.teamflow.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserVO register(RegisterDTO dto) {
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (existing != null) {
            throw new BizException(400, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);
        return toUserVO(user);
    }

    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generateToken(user));
        vo.setUser(toUserVO(user));
        return vo;
    }

    public UserVO me() {
        return toUserVO(currentUser());
    }

    public UserVO updateMe(ProfileUpdateDTO dto) {
        SysUser user = currentUser();
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return toUserVO(user);
    }

    public SysUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(401, "请先登录");
        }
        SysUser user = loginUser.getUser();
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
        return user;
    }

    public UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }
}
