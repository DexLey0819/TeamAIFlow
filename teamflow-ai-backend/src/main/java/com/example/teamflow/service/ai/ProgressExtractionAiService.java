package com.example.teamflow.service.ai;

import com.example.teamflow.ai.AiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProgressExtractionAiService {
    private final AiClient aiClient;

    public Map<String, Object> extractProgress(Long projectId, String reportText, List<Map<String, Object>> tasks) {
        if (!StringUtils.hasText(reportText) || tasks == null || tasks.isEmpty()) {
            return Map.of("updates", List.of());
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下项目成员提交的进度汇报内容，并与提供的 WBS 任务列表匹配，提取其中涉及到的具体任务进度比例。\n\n")
                .append("【汇报内容】\n")
                .append(reportText).append("\n\n")
                .append("【WBS 任务列表】\n");
        for (Map<String, Object> t : tasks) {
            prompt.append("- ID: ").append(t.get("id"))
                    .append(", 任务名称: ").append(t.get("title"))
                    .append("\n");
        }
        prompt.append("\n要求：\n")
                .append("1. 分析汇报内容中明确提到“完成”、“搞定”、“做了多少”的任务，并识别进度百分比（0-100）。\n")
                .append("2. 仅输出 JSON 格式，不要包含任何 Markdown 格式代码块或额外解释，格式如下：\n")
                .append("{\n")
                .append("  \"updates\": [\n")
                .append("    { \"taskId\": \"任务ID\", \"progress\": 80 }\n")
                .append("  ]\n")
                .append("}\n")
                .append("3. 如果汇报中未提及任何任务进度，或信息不足，请输出：{ \"updates\": [] }。\n");

        String responseText = null;
        if (aiClient.enabled()) {
            try {
                responseText = aiClient.chat(prompt.toString());
            } catch (Exception e) {
                // Ignore and fallback
            }
        }

        List<Map<String, Object>> updates = new java.util.ArrayList<>();
        try {
            if (StringUtils.hasText(responseText)) {
                Pattern p = Pattern.compile("(?i)\\{\\s*\"taskId\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"progress\"\\s*:\\s*(\\d+)\\s*\\}");
                Matcher m = p.matcher(responseText);
                while (m.find()) {
                    Map<String, Object> up = new HashMap<>();
                    up.put("taskId", m.group(1));
                    up.put("progress", Integer.parseInt(m.group(2)));
                    updates.add(up);
                }
            }
        } catch (Exception e) {
            // ignore and fallback
        }

        if (updates.isEmpty()) {
            for (Map<String, Object> t : tasks) {
                String title = t.get("title") != null ? t.get("title").toString() : null;
                Object idVal = t.get("id");
                String id = idVal != null ? idVal.toString() : "";
                if (StringUtils.hasText(title)) {
                    if (reportText.contains(title) || (title.length() > 4 && reportText.contains(title.substring(0, 4)))) {
                        int progress = 50;
                        if (reportText.contains("完成100%") || reportText.contains("搞定") || reportText.contains("已完成") || reportText.contains("结束") || reportText.contains("完成了") || reportText.contains("完成了100%")) {
                            progress = 100;
                        } else if (reportText.contains("80%")) {
                            progress = 80;
                        } else if (reportText.contains("60%")) {
                            progress = 60;
                        } else if (reportText.contains("50%")) {
                            progress = 50;
                        } else if (reportText.contains("30%")) {
                            progress = 30;
                        } else if (reportText.contains("完成了一半")) {
                            progress = 50;
                        } else if (reportText.contains("开始") || reportText.contains("进行中")) {
                            progress = 20;
                        }
                        Map<String, Object> up = new HashMap<>();
                        up.put("taskId", id);
                        up.put("progress", progress);
                        updates.add(up);
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("updates", updates);
        return result;
    }
}
