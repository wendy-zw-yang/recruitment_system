package com.example.recruitmentsystem.dto.application;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投递详情（候选人侧 + HR 侧共用结构，按角色裁剪敏感字段）。
 */
@Data
public class ApplicationDetailDto {

    private Long id;

    private Long candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidatePhone;

    /** §5 消息中心：发起会话所需的对方用户 ID（候选人视角 = HR；HR 视角 = 候选人） */
    private Long hrUserId;
    private String hrUserName;

    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String jobDescription;
    private String jobRequirements;

    private Long resumeSnapshotId;
    private String resumeSnapshotName;
    private String resumeSnapshotEmail;
    private String resumeSnapshotPhone;
    private String resumeSnapshotJson;
    private List<String> resumeSnapshotAttachmentUrls;

    private String status;
    private Integer aiScore;
    private String aiReason;

    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;

    private List<HistoryItem> history;
    private List<NoteItem> notes;

    private Boolean withdrawable;
    private Boolean withdrawn;

    @Data
    public static class HistoryItem {
        private String fromStatus;
        private String toStatus;
        private Long changedBy;
        private String changedByName;
        private String note;
        private LocalDateTime changedAt;
    }

    @Data
    public static class NoteItem {
        private Long id;
        private Long hrUserId;
        private String hrUserName;
        private String content;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
