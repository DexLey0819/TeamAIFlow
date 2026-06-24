package com.example.teamflow.security;

import com.example.teamflow.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtil, "expire", 3_600_000L);
        jwtUtil.validateSecret();
    }

    @Test
    void generatedTokenIsValidAndKeepsUsername() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("pm");
        user.setRole("USER");

        String token = jwtUtil.generateToken(user);

        assertTrue(jwtUtil.isValid(token));
        assertEquals("pm", jwtUtil.getUsername(token));
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertFalse(jwtUtil.isValid("not-a-valid-token"));
    }

    @Test
    void shortSecretIsRejected() {
        JwtUtil invalid = new JwtUtil();
        ReflectionTestUtils.setField(invalid, "secret", "short-secret");
        ReflectionTestUtils.setField(invalid, "expire", 3_600_000L);

        assertThrows(IllegalStateException.class, invalid::validateSecret);
    }
}
