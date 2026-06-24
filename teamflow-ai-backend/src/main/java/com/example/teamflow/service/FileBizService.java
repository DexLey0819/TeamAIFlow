package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.ProjectFile;
import com.example.teamflow.entity.ProjectSection;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.ProjectFileMapper;
import com.example.teamflow.mapper.ProjectSectionMapper;
import com.example.teamflow.mapper.SysUserMapper;
import com.example.teamflow.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileBizService {
    private static final String VIEW = "VIEW";
    private static final String EDIT = "EDIT";
    private static final String MANAGE = "MANAGE";

    private final ProjectFileMapper projectFileMapper;
    private final ProjectSectionMapper projectSectionMapper;
    private final SysUserMapper sysUserMapper;
    private final ProjectService projectService;
    private final AuthService authService;
    private final PermissionService permissionService;

    @Value("${file.upload-path}")
    private String uploadPath;

    public FileVO upload(Long projectId, Long sectionId, String documentType, MultipartFile file) {
        projectService.getProject(projectId);
        if (sectionId != null) {
            ProjectSection section = projectSectionMapper.selectById(sectionId);
            if (section == null || !Objects.equals(projectId, section.getProjectId())) {
                throw new BizException(400, "章节不属于当前项目");
            }
            permissionService.requireSectionPermission(sectionId, EDIT);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "上传文件不能为空");
        }
        try {
            Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(base);
            String original = safeOriginalName(file.getOriginalFilename());
            String storedName = UUID.randomUUID() + "_" + original;
            Path target = base.resolve(storedName).normalize();
            if (!target.startsWith(base)) {
                throw new BizException(400, "文件名非法");
            }
            file.transferTo(target);
            SysUser current = authService.currentUser();
            ProjectFile projectFile = new ProjectFile();
            projectFile.setProjectId(projectId);
            projectFile.setSectionId(sectionId);
            projectFile.setUploaderId(current.getId());
            projectFile.setFileName(original);
            projectFile.setFileType(file.getContentType());
            projectFile.setFileUrl(target.toString());
            projectFile.setFileSize(file.getSize());
            projectFile.setDocumentType(StringUtils.hasText(documentType) ? documentType : "OTHER");
            projectFile.setCreateTime(LocalDateTime.now());
            projectFileMapper.insert(projectFile);
            return toVO(projectFile);
        } catch (IOException exception) {
            throw new BizException(500, "文件上传失败：" + exception.getMessage());
        }
    }

    public ProjectFile getInlineImageEntity(Long id) {
        ProjectFile file = getEntity(id);
        if (!isInlineImage(file.getFileType())) {
            throw new BizException(400, "该文件不是可预览图片");
        }
        return file;
    }

    public List<FileVO> listByProject(Long projectId) {
        projectService.getProject(projectId);
        return projectFileMapper.selectList(new LambdaQueryWrapper<ProjectFile>()
                        .eq(ProjectFile::getProjectId, projectId)
                        .orderByDesc(ProjectFile::getCreateTime))
                .stream()
                .filter(this::canListFile)
                .map(this::toVO)
                .toList();
    }

    public ProjectFile getEntity(Long id) {
        ProjectFile file = projectFileMapper.selectById(id);
        if (file == null) {
            throw new BizException(404, "文件不存在");
        }
        requireFileAccess(file);
        return file;
    }

    public Resource loadResource(ProjectFile file) {
        requireFileAccess(file);
        try {
            Resource resource = new UrlResource(Paths.get(file.getFileUrl()).toUri());
            if (!resource.exists()) {
                throw new BizException(404, "文件已不存在");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new BizException(500, "文件路径无效");
        }
    }

    public void delete(Long id) {
        ProjectFile file = getEntity(id);
        SysUser current = authService.currentUser();
        if (!isAdmin(current) && !Objects.equals(current.getId(), file.getUploaderId())) {
            Project project = projectService.getProject(file.getProjectId());
            projectService.ensureManager(project);
        }
        try {
            Files.deleteIfExists(Paths.get(file.getFileUrl()));
        } catch (IOException ignored) {
            // Database cleanup should still proceed when the local file has already been removed.
        }
        projectFileMapper.deleteById(id);
    }

    private FileVO toVO(ProjectFile file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setProjectId(file.getProjectId());
        vo.setSectionId(file.getSectionId());
        vo.setUploaderId(file.getUploaderId());
        SysUser uploader = sysUserMapper.selectById(file.getUploaderId());
        vo.setUploaderName(uploader == null ? "" : uploader.getRealName());
        vo.setFileName(file.getFileName());
        vo.setFileType(file.getFileType());
        vo.setFileUrl("/api/files/download/" + file.getId());
        vo.setFileSize(file.getFileSize());
        vo.setDocumentType(file.getDocumentType());
        vo.setDescription(file.getDescription());
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }

    private String safeOriginalName(String filename) {
        String original = StringUtils.cleanPath(filename == null ? "file" : filename);
        if (!StringUtils.hasText(original)
                || original.contains("..")
                || original.contains("/")
                || original.contains("\\")) {
            throw new BizException(400, "文件名非法");
        }
        Path fileName = Paths.get(original).getFileName();
        if (fileName == null || !StringUtils.hasText(fileName.toString())) {
            throw new BizException(400, "文件名非法");
        }
        return fileName.toString();
    }

    private void requireFileAccess(ProjectFile file) {
        if (file.getSectionId() != null) {
            permissionService.requireSectionPermission(file.getSectionId(), VIEW);
            return;
        }
        projectService.getProject(file.getProjectId());
    }

    private boolean canListFile(ProjectFile file) {
        if (file.getSectionId() == null) {
            return true;
        }
        try {
            List<String> permissions = permissionService.currentSectionPermissions(file.getSectionId());
            return permissions.contains(VIEW) || permissions.contains(MANAGE);
        } catch (BizException exception) {
            return false;
        }
    }

    private boolean isInlineImage(String fileType) {
        return "image/png".equalsIgnoreCase(fileType)
                || "image/jpeg".equalsIgnoreCase(fileType)
                || "image/gif".equalsIgnoreCase(fileType)
                || "image/webp".equalsIgnoreCase(fileType);
    }

    private boolean isAdmin(SysUser user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
