package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_file")
public class ProjectFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sectionId;
    private Long uploaderId;
    private String fileName;
    private String fileType;
    private String fileUrl;
    private Long fileSize;
    private String documentType;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
