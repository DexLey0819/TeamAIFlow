package com.example.teamflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileVO {
    private Long id;
    private Long projectId;
    private Long sectionId;
    private Long uploaderId;
    private String uploaderName;
    private String fileName;
    private String fileType;
    private String fileUrl;
    private Long fileSize;
    private String documentType;
    private String description;
    private LocalDateTime createTime;
}
