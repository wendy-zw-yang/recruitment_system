package com.example.recruitmentsystem.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象。负责简历附件 / 消息附件的统一落盘与读取。
 *
 * <p>存储路径：{@code ${UPLOAD_DIR:./uploads}/{type}/{yyyy-MM}/{uuid}.{ext}}。
 * 命名 UUID + 原扩展名，避免冲突。</p>
 */
public interface FileStorageService {

    /**
     * 保存上传文件。
     *
     * @param file      Spring MVC 包装的多段上传
     * @param type      业务子目录（resume / message / avatar）
     * @return 相对 {@code UPLOAD_DIR} 的存储路径（如 {@code resume/2026-10/abc.pdf}）
     * @throws com.example.recruitmentsystem.common.exception.BusinessException 文件类型 / 大小不合法
     */
    String save(MultipartFile file, String type);

    /**
     * 按相对路径读取文件字节。
     *
     * @param relativePath {@code save} 返回的路径
     * @return 字节内容
     */
    byte[] load(String relativePath);

    /**
     * 按文件名后缀取 MIME（用于下载响应头）。
     */
    String resolveMime(String relativePath);

    /**
     * 把相对路径解析为绝对路径（基于 {@code UPLOAD_DIR}）。不验证文件存在。
     */
    java.nio.file.Path resolveAbsolute(String relativePath);
}
