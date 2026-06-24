package com.example.teamflow.service;

import com.example.teamflow.service.ai.AiRecordService;
import com.example.teamflow.service.ai.ProgressExtractionAiService;
import com.example.teamflow.service.ai.ProjectAiService;
import com.example.teamflow.service.ai.SectionAgentService;
import com.example.teamflow.vo.AiRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiBizService {
    private final ProjectAiService projectAiService;
    private final AiRecordService aiRecordService;
    private final SectionAgentService sectionAgentService;
    private final ProgressExtractionAiService progressExtractionAiService;

    public AiRecordVO weeklyReport(Long projectId) {
        return projectAiService.weeklyReport(projectId);
    }

    public AiRecordVO riskAnalysis(Long projectId) {
        return projectAiService.riskAnalysis(projectId);
    }

    public AiRecordVO documentCheck(Long projectId) {
        return projectAiService.documentCheck(projectId);
    }

    public AiRecordVO summaryReport(Long projectId) {
        return projectAiService.summaryReport(projectId);
    }

    public List<AiRecordVO> records(Long projectId) {
        return aiRecordService.records(projectId);
    }

    public Map<String, Object> extractProgress(Long projectId, String reportText, List<Map<String, Object>> tasks) {
        return progressExtractionAiService.extractProgress(projectId, reportText, tasks);
    }

    public Map<String, String> generateSectionContent(Long projectId, Long sectionId) {
        return sectionAgentService.generateSectionContent(projectId, sectionId);
    }

    public Map<String, String> chatSectionContent(Long projectId, Long sectionId, List<Map<String, String>> messages) {
        return sectionAgentService.chatSectionContent(projectId, sectionId, messages);
    }

}
