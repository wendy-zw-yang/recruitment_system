package com.example.recruitmentsystem.service.impl;

import com.example.recruitmentsystem.common.exception.BusinessException;
import com.example.recruitmentsystem.service.ResumeTextExtractService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 默认实现：先尝试 Python 子进程（{@code tools/parse_resume.py}），失败回退 PDFBox / POI。
 */
@Slf4j
@Service
public class ResumeTextExtractServiceImpl implements ResumeTextExtractService {

    private static final long PROCESS_TIMEOUT_SECONDS = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String extractText(String absolutePath) {
        Path path = Paths.get(absolutePath).toAbsolutePath().normalize();
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException(404, "文件不存在");
        }

        String byPython = tryPython(file);
        if (byPython != null) {
            return byPython;
        }
        log.info("[ResumeTextExtract] Python 不可用，回退 Java (path={})", path);
        return extractWithJava(file);
    }

    private String tryPython(File file) {
        Path script = Paths.get("tools", "parse_resume.py").toAbsolutePath();
        if (!script.toFile().exists()) {
            return null;
        }
        ProcessBuilder pb = new ProcessBuilder("python", script.toString(), file.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[ResumeTextExtract] Python 进程超时");
                return null;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                log.warn("[ResumeTextExtract] Python 退出码 {} , 回退 Java", exit);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String stdout = sb.toString().trim();
                if (stdout.isEmpty()) {
                    return null;
                }
                JsonNode node = objectMapper.readTree(stdout);
                JsonNode textNode = node.get("text");
                if (textNode == null || !textNode.isTextual()) {
                    return null;
                }
                return textNode.asText();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[ResumeTextExtract] Python 调用失败: {}", e.getMessage());
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String extractWithJava(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(file)) {
                    return new PDFTextStripper().getText(doc);
                }
            }
            if (name.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(openStream(file));
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    return extractor.getText();
                }
            }
        } catch (IOException e) {
            log.error("[ResumeTextExtract] Java 解析失败", e);
            throw new BusinessException(500, "简历解析失败");
        }
        throw new BusinessException(400, "仅支持 PDF / .docx 格式");
    }

    private static java.io.InputStream openStream(File file) throws IOException {
        return new java.io.FileInputStream(file);
    }
}
