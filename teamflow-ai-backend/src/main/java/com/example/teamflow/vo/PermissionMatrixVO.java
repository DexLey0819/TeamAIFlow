package com.example.teamflow.vo;

import lombok.Data;
import java.util.List;

@Data
public class PermissionMatrixVO {
    private List<RoleRow> roleRows;
    private List<SectionColumn> sectionColumns;
    private List<PermissionEntry> permissions;

    @Data
    public static class RoleRow {
        private Long roleId;
        private String roleCode;
        private String roleName;
    }

    @Data
    public static class SectionColumn {
        private Long sectionId;
        private String sectionCode;
        private String sectionName;
    }

    @Data
    public static class PermissionEntry {
        private Long roleId;
        private Long sectionId;
        private List<String> permissionCodes;
    }
}
