package com.example.recruitmentsystem.service.impl;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件系统实现。目录不存在时自动创建。
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    /** 允许的文件扩展名（小写） */
    private static final Map<String, Set<String>> ALLOWED_EXT_BY_TYPE = Map.of(
            "resume", Set.of("pdf", "docx"),
            "message", Set.of("pdf", "docx", "doc", "png", "jpg", "jpeg"),
            "avatar", Set.of("png", "jpg", "jpeg")
    );

    private static final long MAX_BYTES = 20L * 1024 * 1024; // 20 MB

    private final Path uploadRoot;

    public FileStorageServiceImpl(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + this.uploadRoot, e);
        }
    }

    @Override
    public String save(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(400, "文件不能超过 20MB");
        }

        String original = file.getOriginalFilename();
        String ext = FilenameUtils.getExtension(original == null ? "" : original).toLowerCase(Locale.ROOT);
        Set<String> allowed = ALLOWED_EXT_BY_TYPE.getOrDefault(type, Set.of());
        if (ext.isEmpty() || !allowed.contains(ext)) {
            throw new BusinessException(400, "文件类型不支持: " + ext);
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String stored = UUID.randomUUID() + "." + ext;
        Path target = uploadRoot.resolve(Paths.get(type, dateDir, stored)).normalize();

        if (!target.startsWith(uploadRoot)) {
            throw new BusinessException(400, "非法文件路径");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件保存失败: {}", target, e);
            throw new BusinessException(500, "文件保存失败");
        }
        String relative = uploadRoot.relativize(target).toString().replace('\\', '/');
        log.info("[FileStorage] saved: {} ({} bytes)", relative, file.getSize());
        return relative;
    }

    @Override
    public byte[] load(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessException(404, "文件不存在");
        }
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BusinessException(404, "文件不存在");
        }
        if (!Files.exists(target)) {
            throw new BusinessException(404, "文件不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException(500, "文件读取失败");
        }
    }

    @Override
    public String resolveMime(String relativePath) {
        if (relativePath == null) return "application/octet-stream";
        String ext = FilenameUtils.getExtension(relativePath).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    @Override
    public Path resolveAbsolute(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new com.example.recruitmentsystem.common.exception.BusinessException(404, "文件路径为空");
        }
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new com.example.recruitmentsystem.common.exception.BusinessException(404, "文件不存在");
        }
        return target;
    }

    /** 暴露给测试 / 其他模块使用 */
    public long getMaxBytes() {
        return MAX_BYTES;
    }

    /** 暴露给测试 / 其他模块使用（多余 DataSize 静态导入消除警告） */
    @SuppressWarnings("unused")
    private static final DataSize UNUSED = DataSize.ofMegabytes(20);
}
