package com.example.recruitmentsystem.controller.message;

import com.example.recruitmentsystem.common.Result;
import com.example.recruitmentsystem.common.annotation.LoginRequired;
import com.example.recruitmentsystem.common.context.CurrentUserContext;
import com.example.recruitmentsystem.dto.message.AttachmentUploadRef;
import com.example.recruitmentsystem.service.message.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * §5 消息中心 - 附件上传 / 下载 Controller。
 */
@LoginRequired
@RestController
@RequestMapping("/api/messages/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /** 上传（仅落盘，不入库；返回临时引用供 sendMessage 绑定） */
    @PostMapping("/upload")
    public Result<AttachmentUploadRef> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(attachmentService.upload(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole(), file));
    }

    /** 下载（鉴权：仅会话参与者） */
    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) {
        AttachmentService.DownloadPayload p = attachmentService.download(
                CurrentUserContext.getUserId(), CurrentUserContext.getRole(), id);
        // RFC 5987 文件名编码
        String fileName = p.fileName() == null ? "attachment" : p.fileName();
        String encoded = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build());
        headers.add("Access-Control-Expose-Headers", "Content-Disposition");
        MediaType mt = p.mime() != null ? MediaType.parseMediaType(p.mime()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mt)
                .contentLength(p.bytes().length)
                .body(new ByteArrayResource(p.bytes()));
    }
}
