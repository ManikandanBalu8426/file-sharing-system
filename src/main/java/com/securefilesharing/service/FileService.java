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
        // Create upload directory if not exists
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = file.getOriginalFilename();
        String encryptedFileName = UUID.randomUUID().toString() + "_" + fileName + ".enc";
        Path path = Paths.get(uploadDir + File.separator + encryptedFileName);

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

        auditService.logAction(owner, "UPLOAD", "Uploaded file: " + fileName);
        return fileRepository.save(fileEntity);
    }

    @Transactional(readOnly = true)
    public byte[] downloadFile(Long fileId, User requester) throws Exception {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!canDownloadContent(requester, fileEntity)) {
            auditService.logAction(requester, "DOWNLOAD_DENIED",
                    "Denied download for fileId=" + fileEntity.getId() + ", name=" + fileEntity.getFileName());
            throw new RuntimeException("Unauthorized Access");
        }

        Path path = Paths.get(fileEntity.getEncryptedPath());
        byte[] encryptedBytes = Files.readAllBytes(path);
        byte[] decryptedBytes = decrypt(encryptedBytes);

        auditService.logAction(requester, "DOWNLOAD", "Downloaded file: " + fileEntity.getFileName());
        return decryptedBytes;
    }

    @Transactional(readOnly = true)
    public List<FileMetadataDto> listVisibleFiles(User requester) {
        if (requester.getRole() == Role.ROLE_AUDITOR) {
            return List.of();
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
