package com.example.teamflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExportRequestDTO {
    private String exportScope;
    private List<Long> includeSections;
    private Boolean includeTasks;
    private Boolean includeProgress;
    private Boolean includeAi;
    private Boolean includeStatistics;
}
