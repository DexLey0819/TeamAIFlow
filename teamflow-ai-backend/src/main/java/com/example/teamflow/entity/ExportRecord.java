package com.example.teamflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("export_record")
public class ExportRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long creatorId;
    private String exportScope;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String status;
    private String failureReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
