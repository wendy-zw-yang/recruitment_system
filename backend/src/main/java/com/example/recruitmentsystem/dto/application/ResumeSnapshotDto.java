package com.example.recruitmentsystem.dto.application;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历快照 DTO（HR 查看候选人投递时的简历）。
 *
 * <p>即使候选人后续修改 / 归档简历也不影响快照内容。</p>
 */
@Data
public class ResumeSnapshotDto {

    private Long resumeId;
    private LocalDateTime snapshotAt;
    private String fullName;
    private String email;
    private String phone;
    private String location;
    private String selfIntro;

    /** 结构化 JSON 字符串（含 education / work / projects / skills 数组） */
    private String structuredJson;

    /** 附件下载链接列表（带 token 或仅 HR 内网访问，按实现定） */
    private List<AttachmentItem> attachments;

    @Data
    public static class AttachmentItem {
        private Long id;
        private String fileName;
        private String url;
        private Long sizeBytes;
    }
}
