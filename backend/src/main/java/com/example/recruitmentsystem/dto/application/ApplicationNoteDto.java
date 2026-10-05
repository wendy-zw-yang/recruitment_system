package com.example.recruitmentsystem.dto.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备注 DTO（HR 内部备注，仅 HR 自己可见）。
 */
@Data
public class ApplicationNoteDto {

    private Long id;
    private Long applicationId;
    private Long hrUserId;
    private String hrUserName;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class CreateRequest {
        @NotBlank(message = "备注内容不能为空")
        @Size(max = 2000, message = "备注内容不能超过 2000 字")
        private String content;
    }

    @Data
    public static class UpdateRequest {
        @NotBlank(message = "备注内容不能为空")
        @Size(max = 2000, message = "备注内容不能超过 2000 字")
        private String content;
    }
}
