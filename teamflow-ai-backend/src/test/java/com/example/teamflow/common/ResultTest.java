package com.example.teamflow.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultTest {

    @Test
    void successWithoutDataUsesStandardSuccessShape() {
        Result<Object> result = Result.success();

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void successWithDataKeepsPayload() {
        Result<String> result = Result.success("payload");

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("payload", result.getData());
    }

    @Test
    void failWithoutCodeUsesServerErrorCode() {
        Result<Object> result = Result.fail("操作失败");

        assertEquals(500, result.getCode());
        assertEquals("操作失败", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void failWithCodeKeepsCustomCode() {
        Result<Object> result = Result.fail(403, "没有权限执行该操作");

        assertEquals(403, result.getCode());
        assertEquals("没有权限执行该操作", result.getMessage());
        assertNull(result.getData());
    }
}
