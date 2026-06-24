package com.example.teamflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.Notification;
import com.example.teamflow.entity.Project;
import com.example.teamflow.entity.SysUser;
import com.example.teamflow.mapper.NotificationMapper;
import com.example.teamflow.mapper.ProjectMapper;
import com.example.teamflow.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final AuthService authService;
    private final NotificationMapper notificationMapper;
    private final ProjectMapper projectMapper;

    public void notifyUser(Long userId, Long projectId, String title, String content, String type, String link) {
        LocalDateTime now = LocalDateTime.now();
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setProjectId(projectId);
        notification.setTitle(title);
        notification.setContent(withLink(content, link));
        notification.setType(type);
        notification.setReadFlag(0);
        notification.setCreateTime(now);
        notification.setUpdateTime(now);
        notificationMapper.insert(notification);
    }

    public List<NotificationVO> myNotifications() {
        SysUser user = authService.currentUser();
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, user.getId())
                        .orderByDesc(Notification::getCreateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public long unreadCount() {
        SysUser user = authService.currentUser();
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, user.getId())
                .eq(Notification::getReadFlag, 0));
    }

    public void markRead(Long id) {
        SysUser user = authService.currentUser();
        Notification notification = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, user.getId())
                .last("limit 1"));
        if (notification == null) {
            throw new BizException(404, "通知不存在");
        }
        if (notification.getReadFlag() != null && notification.getReadFlag() == 1) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        notification.setReadFlag(1);
        notification.setReadTime(now);
        notification.setUpdateTime(now);
        notificationMapper.updateById(notification);
    }

    public void markAllRead() {
        SysUser user = authService.currentUser();
        LocalDateTime now = LocalDateTime.now();
        Notification notification = new Notification();
        notification.setReadFlag(1);
        notification.setReadTime(now);
        notification.setUpdateTime(now);
        notificationMapper.update(notification, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, user.getId())
                .eq(Notification::getReadFlag, 0));
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setUserId(notification.getUserId());
        vo.setProjectId(notification.getProjectId());
        vo.setProjectName(projectName(notification.getProjectId()));
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setReadFlag(notification.getReadFlag());
        vo.setReadTime(notification.getReadTime());
        vo.setCreateTime(notification.getCreateTime());
        return vo;
    }

    private String projectName(Long projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        return project == null ? null : project.getProjectName();
    }

    private String withLink(String content, String link) {
        if (!StringUtils.hasText(link)) {
            return content;
        }
        String baseContent = content == null ? "" : content;
        return baseContent + "\n链接：" + link;
    }
}
