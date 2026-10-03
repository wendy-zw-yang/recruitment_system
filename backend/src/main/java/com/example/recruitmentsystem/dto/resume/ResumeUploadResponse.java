package com.example.recruitmentsystem.dto.resume;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 简历上传结果。前端据此判断是否提示「AI 解析失败，请手动填写」。
 */
@Data
@AllArgsConstructor
public class ResumeUploadResponse {

    private ResumeDto resume;

    /** AI-1 是否成功；false 时前端 toast 提示用户手动填写 */
    private boolean aiParsed;
}
