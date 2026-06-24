package com.example.teamflow.controller;

import com.example.teamflow.common.Result;
import com.example.teamflow.entity.ProjectFile;
import com.example.teamflow.service.FileBizService;
import com.example.teamflow.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileBizService fileBizService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileVO> upload(@RequestParam Long projectId,
                                 @RequestParam(required = false) Long sectionId,
                                 @RequestParam(required = false) String documentType,
                                 @RequestPart("file") MultipartFile file) {
        return Result.success(fileBizService.upload(projectId, sectionId, documentType, file));
    }

    @GetMapping("/project/{projectId}")
    public Result<List<FileVO>> listByProject(@PathVariable Long projectId) {
        return Result.success(fileBizService.listByProject(projectId));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ProjectFile file = fileBizService.getEntity(id);
        Resource resource = fileBizService.loadResource(file);
        String encoded = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encoded + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/inline/{id}")
    public ResponseEntity<Resource> inline(@PathVariable Long id) {
        ProjectFile file = fileBizService.getInlineImageEntity(id);
        Resource resource = fileBizService.loadResource(file);
        MediaType mediaType = MediaType.parseMediaType(file.getFileType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(mediaType)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileBizService.delete(id);
        return Result.success();
    }
}
