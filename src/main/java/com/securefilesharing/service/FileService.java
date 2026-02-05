package com.securefilesharing.service;

import com.securefilesharing.dto.FileMetadataDto;
import com.securefilesharing.entity.FileEntity;
import com.securefilesharing.entity.AccessRequestStatus;
import com.securefilesharing.entity.AccessType;
import com.securefilesharing.entity.Role;
import com.securefilesharing.entity.User;
import com.securefilesharing.entity.VisibilityType;
import com.securefilesharing.repository.FileRepository;
import com.securefilesharing.repository.FileAccessRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private FileAccessRequestRepository fileAccessRequestRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${app.protected-access.ttlSeconds:3600}")
    private long protectedAccessTtlSeconds;

    private static final String ALGORITHM = "AES";
    private static final byte[] KEY = "MySuperSecretKey".getBytes(); // 16 bytes for AES-128

    private static final String ENCRYPTED_EXTENSION = ".enc";
    private static final String DIR_IMAGES = "images";
    private static final String DIR_VIDEOS = "videos";
    private static final String DIR_AUDIO = "audio";
    private static final String DIR_DOCUMENTS = "documents";

    public FileEntity uploadFile(MultipartFile file, User owner) throws Exception {
        return uploadFile(file, owner, VisibilityType.PRIVATE, null, null);
    }

    public FileEntity uploadFile(
            MultipartFile file,
            User owner,
            VisibilityType visibilityType,
            String purpose,
            String category
    ) throws Exception {
        // Ensure base storage directory exists
        File baseDir = new File(uploadDir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        String fileName = sanitizeOriginalFilename(file.getOriginalFilename());
        String categoryDirName = resolveCategoryDir(file, fileName);

        // Ensure category subdirectory exists
        Path categoryDir = Paths.get(uploadDir).resolve(categoryDirName);
        File categoryDirFile = categoryDir.toFile();
        if (!categoryDirFile.exists()) {
            categoryDirFile.mkdirs();
        }

        String encryptedFileName = UUID.randomUUID().toString() + "_" + fileName + ENCRYPTED_EXTENSION;
        Path path = categoryDir.resolve(encryptedFileName);

        // Encrypt content
        byte[] fileBytes = file.getBytes();
        byte[] encryptedBytes = encrypt(fileBytes);

        // Save to disk
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            fos.write(encryptedBytes);
        }

        // Save to DB
        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(fileName);
        fileEntity.setEncryptedPath(path.toString());
        fileEntity.setOwner(owner);
        fileEntity.setUploadTimestamp(LocalDateTime.now());
        fileEntity.setSizeBytes(file.getSize());
        fileEntity.setContentType(file.getContentType());
        fileEntity.setVisibilityType(visibilityType == null ? VisibilityType.PRIVATE : visibilityType);
        fileEntity.setPurpose(purpose);
        fileEntity.setCategory(category);

        FileEntity saved = fileRepository.save(fileEntity);
        auditService.logAction(owner, "UPLOAD",
                "Uploaded fileId=" + saved.getId() + ", name=" + fileName,
                saved.getId(), owner.getId(), null);
        return saved;
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        // Avoid path traversal / client-supplied paths
        String baseName = Paths.get(originalFilename).getFileName().toString();
        // Keep filename readable; remove common problematic characters
        String cleaned = baseName.replace("\\u0000", "").replaceAll("[\\r\\n]", "").trim();
        return cleaned.isBlank() ? "file" : cleaned;
    }

    private String resolveCategoryDir(MultipartFile file, String safeFileName) {
        String contentType = file != null ? file.getContentType() : null;

        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.startsWith("image/")) return DIR_IMAGES;
            if (ct.startsWith("video/")) return DIR_VIDEOS;
            if (ct.startsWith("audio/")) return DIR_AUDIO;

            // Common document-like content types
            if (ct.equals("application/pdf")
                    || ct.startsWith("text/")
                    || ct.contains("officedocument")
                    || ct.equals("application/msword")
                    || ct.equals("application/rtf")
                    || ct.equals("application/vnd.ms-excel")
                    || ct.equals("application/vnd.ms-powerpoint")) {
                return DIR_DOCUMENTS;
            }
        }

        String ext = getFileExtension(safeFileName);
        if (ext == null) {
            return DIR_DOCUMENTS;
        }

        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "avif", "svg" -> DIR_IMAGES;
            case "mp4", "mov", "avi", "mkv", "webm", "m4v" -> DIR_VIDEOS;
            case "mp3", "wav", "flac", "aac", "ogg", "m4a" -> DIR_AUDIO;
            default -> DIR_DOCUMENTS;
        };
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return null;
        String name = fileName.trim();
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0 || lastDot == name.length() - 1) return null;
        return name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void deleteFile(Long fileId, User requester) {
        if (requester == null) throw new RuntimeException("Unauthorized");
        if (requester.getRole() != Role.ROLE_USER) {
            throw new RuntimeException("Only USER can delete files");
        }

        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        boolean isOwner = fileEntity.getOwner() != null
                && fileEntity.getOwner().getId() != null
                && fileEntity.getOwner().getId().equals(requester.getId());
        if (!isOwner) {
            throw new RuntimeException("Only the file owner can delete this file");
        }

        // Remove access requests for the file.
        fileAccessRequestRepository.deleteByFileId(fileId);

        // Remove from disk best-effort.
        try {
            if (fileEntity.getEncryptedPath() != null) {
                Files.deleteIfExists(Paths.get(fileEntity.getEncryptedPath()));
            }
        } catch (Exception ignored) {
            // best-effort disk cleanup
        }

        fileRepository.delete(fileEntity);
        auditService.logAction(requester, "DELETE",
                "Deleted fileId=" + fileId + ", name=" + fileEntity.getFileName(),
                fileId, requester.getId(), null);
    }

    @Transactional
    public FileMetadataDto updateVisibility(Long fileId, VisibilityType newVisibility, User requester) {
        if (requester == null) throw new RuntimeException("Unauthorized");
        if (requester.getRole() != Role.ROLE_USER) {
            throw new RuntimeException("Only USER can change visibility");
        }
        if (newVisibility == null) {
            throw new RuntimeException("Visibility is required");
        }

        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        boolean isOwner = fileEntity.getOwner() != null
                && fileEntity.getOwner().getId() != null
                && fileEntity.getOwner().getId().equals(requester.getId());
        if (!isOwner) {
            throw new RuntimeException("Only the file owner can change visibility");
        }

        VisibilityType old = fileEntity.getVisibilityType() == null ? VisibilityType.PRIVATE : fileEntity.getVisibilityType();
        fileEntity.setVisibilityType(newVisibility);
        FileEntity saved = fileRepository.save(fileEntity);

        // If the file is no longer PROTECTED, clear access requests (they are no longer meaningful).
        if (newVisibility != VisibilityType.PROTECTED) {
            fileAccessRequestRepository.deleteByFileId(fileId);
        }

        auditService.logAction(requester, "VISIBILITY_CHANGE",
                "Changed visibility for fileId=" + fileId + " from " + old + " to " + newVisibility,
                fileId, requester.getId(), null);

        return toMetadataDto(requester, saved);
    }

    @Transactional(readOnly = true)
    public byte[] downloadFile(Long fileId, User requester) throws Exception {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!canDownloadContent(requester, fileEntity)) {
            auditService.logAction(requester, "DOWNLOAD_DENIED",
                "Denied download for fileId=" + fileEntity.getId() + ", name=" + fileEntity.getFileName(),
                fileEntity.getId(), fileEntity.getOwner() != null ? fileEntity.getOwner().getId() : null, null);
            throw new RuntimeException("Unauthorized Access");
        }

        Path path = Paths.get(fileEntity.getEncryptedPath());
        byte[] encryptedBytes = Files.readAllBytes(path);
        byte[] decryptedBytes = decrypt(encryptedBytes);

        auditService.logAction(requester, "DOWNLOAD",
            "Downloaded fileId=" + fileEntity.getId() + ", name=" + fileEntity.getFileName(),
            fileEntity.getId(), fileEntity.getOwner() != null ? fileEntity.getOwner().getId() : null, null);
        return decryptedBytes;
    }

    @Transactional(readOnly = true)
    public List<FileMetadataDto> listVisibleFiles(User requester) {
        if (requester.getRole() == Role.ROLE_AUDITOR) {
            return List.of();
        }

        // USER is a data owner: never list other users' files.
        if (requester.getRole() == Role.ROLE_USER) {
            return fileRepository.findByOwner(requester)
                    .stream()
                    .map(f -> toMetadataDto(requester, f))
                    .collect(Collectors.toList());
        }

        List<FileEntity> all = fileRepository.findAll();
        List<FileMetadataDto> result = all.stream()
                .filter(f -> canViewMetadata(requester, f))
                .map(f -> toMetadataDto(requester, f))
                .collect(Collectors.toList());

        if (requester.getRole() == Role.ROLE_ADMIN) {
            auditService.logAction(requester, "METADATA_VIEW", "Viewed file metadata list; count=" + result.size());
        }
        return result;
    }

    public FileEntity getFile(Long id) {
        return fileRepository.findById(id).orElse(null);
    }

    private FileMetadataDto toMetadataDto(User requester, FileEntity file) {
        FileMetadataDto dto = new FileMetadataDto();
        dto.setId(file.getId());
        dto.setFileName(file.getFileName());
        dto.setOwnerUsername(file.getOwner() != null ? file.getOwner().getUsername() : null);
        dto.setSizeBytes(file.getSizeBytes() == null ? 0L : file.getSizeBytes());
        dto.setUploadTimestamp(file.getUploadTimestamp());
        dto.setVisibilityType(file.getVisibilityType() == null ? VisibilityType.PRIVATE : file.getVisibilityType());

        dto.setCanDownload(canDownloadContent(requester, file));

        if (canViewSensitiveMetadata(requester, file)) {
            dto.setPurpose(file.getPurpose());
            dto.setCategory(file.getCategory());
        }

        return dto;
    }

    private boolean canViewMetadata(User requester, FileEntity file) {
        if (requester == null || file == null) return false;
        if (requester.getRole() == Role.ROLE_AUDITOR) return false;

        boolean isOwner = file.getOwner() != null && file.getOwner().getId() != null
                && file.getOwner().getId().equals(requester.getId());
        if (isOwner) return true;

        if (requester.getRole() == Role.ROLE_USER) {
            // USER must never see other users' files (even PUBLIC/PROTECTED).
            return false;
        }

        VisibilityType visibility = file.getVisibilityType();
        if (visibility == null) visibility = VisibilityType.PRIVATE;

        return switch (visibility) {
            case PRIVATE -> false; // PRIVATE → only owner, zero admin visibility
            case PUBLIC -> true;   // PUBLIC → organization-level visibility
            case PROTECTED -> requester.getRole() == Role.ROLE_ADMIN || hasActiveApproval(requester, file, AccessType.VIEW);
        };
    }

    private boolean canViewSensitiveMetadata(User requester, FileEntity file) {
        if (!canViewMetadata(requester, file)) return false;

        boolean isOwner = file.getOwner() != null && file.getOwner().getId() != null
                && file.getOwner().getId().equals(requester.getId());
        if (isOwner) return true;

        VisibilityType visibility = file.getVisibilityType();
        if (visibility == null) visibility = VisibilityType.PRIVATE;

        // Sensitive metadata is only for owner, or for users who have an approved, non-expired request,
        // or for admins when the file is PUBLIC (since content is allowed).
        if (visibility == VisibilityType.PUBLIC && requester.getRole() == Role.ROLE_ADMIN) {
            return true;
        }

        return hasActiveApproval(requester, file, AccessType.VIEW);
    }

    private boolean canDownloadContent(User requester, FileEntity file) {
        if (requester == null || file == null) return false;
        if (requester.getRole() == Role.ROLE_AUDITOR) return false;

        boolean isOwner = file.getOwner() != null && file.getOwner().getId() != null
                && file.getOwner().getId().equals(requester.getId());
        if (isOwner) return true;

        if (requester.getRole() == Role.ROLE_USER) {
            // USER cannot download other users' files.
            return false;
        }

        VisibilityType visibility = file.getVisibilityType();
        if (visibility == null) visibility = VisibilityType.PRIVATE;

        return switch (visibility) {
            case PRIVATE -> false;
            case PUBLIC -> true;
            case PROTECTED -> hasActiveApproval(requester, file, AccessType.DOWNLOAD);
        };
    }

    private boolean hasActiveApproval(User requester, FileEntity file, AccessType needed) {
        if (requester == null || file == null) return false;
        LocalDateTime now = LocalDateTime.now();

        return fileAccessRequestRepository
                .findFirstByFileIdAndRequesterIdAndStatusAndExpiresAtAfterOrderByDecidedAtDesc(
                        file.getId(),
                        requester.getId(),
                        AccessRequestStatus.APPROVED,
                        now
                )
                .filter(r -> {
                    // VIEW approval implies metadata access; DOWNLOAD approval implies both view+download.
                    if (needed == AccessType.VIEW) return true;
                    return r.getAccessType() == AccessType.DOWNLOAD;
                })
                .isPresent();
    }

    private byte[] encrypt(byte[] data) throws Exception {
        Key key = new SecretKeySpec(KEY, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    private byte[] decrypt(byte[] data) throws Exception {
        Key key = new SecretKeySpec(KEY, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }
}
