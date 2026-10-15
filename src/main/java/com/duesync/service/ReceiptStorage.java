package com.duesync.service;

import com.duesync.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class ReceiptStorage {

    private static final Map<String, MediaType> ALLOWED = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "webp", MediaType.parseMediaType("image/webp"),
            "heic", MediaType.parseMediaType("image/heic"),
            "pdf", MediaType.APPLICATION_PDF
    );

    private final Path uploadDir;

    public ReceiptStorage(@Value("${duesync.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please attach your payment receipt.");
        }
        String extension = extension(file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean typeOk = contentType.startsWith("image/") || contentType.equals("application/pdf");
        if (!ALLOWED.containsKey(extension) || !typeOk) {
            throw ApiException.badRequest("The receipt must be an image (JPG, PNG, WEBP, HEIC) or a PDF.");
        }
        String name = UUID.randomUUID() + "." + extension;
        try (InputStream input = file.getInputStream()) {
            Files.createDirectories(uploadDir);
            Files.copy(input, uploadDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save receipt", e);
        }
        return name;
    }

    public Resource load(String name) {
        Path path = resolve(name);
        if (path == null || !Files.isRegularFile(path)) {
            throw ApiException.notFound("Receipt file not found.");
        }
        return new FileSystemResource(path);
    }

    public MediaType mediaType(String name) {
        return ALLOWED.getOrDefault(extension(name), MediaType.APPLICATION_OCTET_STREAM);
    }

    public void delete(String name) {
        Path path = resolve(name);
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private Path resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Path path = uploadDir.resolve(name).normalize();
        return path.startsWith(uploadDir) ? path : null;
    }
}
