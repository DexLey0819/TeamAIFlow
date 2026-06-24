package com.example.teamflow.ai;

import com.example.teamflow.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 120_000;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.provider:zhipu}")
    private String provider;

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.model}")
    private String model;

    public boolean enabled() {
        return StringUtils.hasText(apiKey) && !"demo-key".equals(apiKey) && !"your-api-key".equals(apiKey);
    }

    public String chat(String prompt) {
        if (!enabled()) {
            return localFallback(prompt);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2048);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是 TeamFlowAI 的项目管理智能体。只基于已提供数据分析，不编造未出现内容。"),
                Map.of("role", "user", "content", prompt)
        ));

        ResponseEntity<Map> response;
        try {
            response = restTemplate().postForEntity(apiUrl, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientException exception) {
            throw new BizException(502, "AI 服务调用失败：" + exception.getMessage());
        }
        Object choicesObj = response.getBody() == null ? null : response.getBody().get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object message = firstMap.get("message");
                if (message instanceof Map<?, ?> messageMap && messageMap.get("content") != null) {
                    return messageMap.get("content").toString();
                }
            }
        }
        throw new BizException(500, "AI 返回结果格式异常");
    }

    public String chat(List<Map<String, String>> messages) {
        if (!enabled()) {
            return localFallback(messages.isEmpty() ? "" : messages.get(messages.size() - 1).get("content"));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2048);
        body.put("messages", messages);

        ResponseEntity<Map> response;
        try {
            response = restTemplate().postForEntity(apiUrl, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientException exception) {
            throw new BizException(502, "AI 服务调用失败：" + exception.getMessage());
        }
        Object choicesObj = response.getBody() == null ? null : response.getBody().get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object message = firstMap.get("message");
                if (message instanceof Map<?, ?> messageMap && messageMap.get("content") != null) {
                    return messageMap.get("content").toString();
                }
            }
        }
        throw new BizException(500, "AI 返回结果格式异常");
    }

    private String localFallback(String prompt) {
        return """
                【TeamFlowAI 本地演示结果】
                当前未配置可用的 %s API Key，系统已启用本地备用分析。

                依据已提交的项目数据，建议重点关注：
                1. 梳理近期任务完成情况，确认阻塞项是否已有负责人和解决时间。
                2. 检查需求、设计、开发、测试文档是否覆盖项目当前阶段的核心交付物。
                3. 对进度滞后、成员贡献不均、文档长时间未更新等风险建立跟踪记录。

                原始分析请求摘要：
                %s
                """.formatted(provider, prompt == null ? "" : prompt);
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return new RestTemplate(requestFactory);
    }
}
