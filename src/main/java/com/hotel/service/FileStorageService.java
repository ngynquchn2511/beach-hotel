package com.hotel.service;

import com.hotel.entity.MediaType;
import com.hotel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
    private static final List<String> VIDEO_EXTENSIONS = List.of("mp4", "webm", "mov");

    // Gioi han dung luong tung file (gioi han multipart cua server dat cao hon de nhan duoc video)
    public static final int MAX_IMAGE_MB = 5;
    public static final int MAX_VIDEO_MB = 30;
    private static final long MB = 1024L * 1024;

    // Ket qua luu 1 tep dinh kem danh gia
    public record StoredMedia(String url, MediaType type) {
    }

    // Thu muc goc chua toan bo file upload, VD: uploads/rooms, uploads/combos
    @Value("${app.upload.base-dir}")
    private String baseUploadDir;

    // Luu file anh vao thu muc con (VD: "rooms" hoac "combos"), tra ve duong dan web /uploads/<subDir>/<file>
    public String store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("Chỉ chấp nhận file ảnh định dạng jpg, jpeg, png hoặc webp");
        }
        if (file.getSize() > MAX_IMAGE_MB * MB) {
            throw new BusinessException("Mỗi ảnh tối đa " + MAX_IMAGE_MB + "MB");
        }
        return save(file, subDir, extension);
    }

    // Anh / video khach dinh kem khi danh gia: anh jpg/png/webp toi da 5MB, video mp4/webm/mov toi da 30MB
    public StoredMedia storeReviewMedia(MultipartFile file) {
        String extension = getExtension(file.getOriginalFilename()).toLowerCase();
        MediaType type;
        if (ALLOWED_EXTENSIONS.contains(extension)) {
            type = MediaType.IMAGE;
            if (file.getSize() > MAX_IMAGE_MB * MB) {
                throw BusinessException.of("err.mediaImageTooLarge", MAX_IMAGE_MB);
            }
        } else if (VIDEO_EXTENSIONS.contains(extension)) {
            type = MediaType.VIDEO;
            if (file.getSize() > MAX_VIDEO_MB * MB) {
                throw BusinessException.of("err.mediaVideoTooLarge", MAX_VIDEO_MB);
            }
        } else {
            throw BusinessException.of("err.mediaType");
        }
        // Chan file doi duoi (VD .exe doi thanh .mp4): loai noi dung trinh duyet bao phai khop
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equals(contentType)
                && !contentType.startsWith(type == MediaType.IMAGE ? "image/" : "video/")) {
            throw BusinessException.of("err.mediaType");
        }
        return new StoredMedia(save(file, "reviews", extension), type);
    }

    private String save(MultipartFile file, String subDir, String extension) {
        try {
            Path uploadPath = Paths.get(baseUploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedFileName = UUID.randomUUID() + "." + extension;
            Path targetPath = uploadPath.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + subDir + "/" + storedFileName;
        } catch (IOException ex) {
            throw new BusinessException("Không thể lưu file ảnh, vui lòng thử lại");
        }
    }

    // Xoa file anh vat ly khi phong/combo bi xoa hoac doi anh moi
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        try {
            // imageUrl dang co dang /uploads/<subDir>/<file>
            String relativePath = imageUrl.substring("/uploads/".length());
            Path filePath = Paths.get(baseUploadDir).toAbsolutePath().normalize().resolve(relativePath);
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {
            // Khong chan luong nghiep vu chinh neu xoa file that bai
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException("File không hợp lệ");
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
