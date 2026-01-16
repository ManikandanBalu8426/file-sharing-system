package com.securefilesharing.service;

import com.securefilesharing.entity.FileEntity;
import com.securefilesharing.entity.Role;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private AuditService auditService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final String ALGORITHM = "AES";
    private static final byte[] KEY = "MySuperSecretKey".getBytes(); // 16 bytes for AES-128

    public FileEntity uploadFile(MultipartFile file, User owner) throws Exception {
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
        // Save to DB
        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(fileName);
        fileEntity.setEncryptedPath(path.toString());
        fileEntity.setOwner(owner);
        fileEntity.setUploadTimestamp(LocalDateTime.now());

        auditService.logAction(owner, "UPLOAD", "Uploaded file: " + fileName);
        return fileRepository.save(fileEntity);
    }

    public byte[] downloadFile(Long fileId, User requester) throws Exception {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // Check permission (Admin can view all, User can view own)
        if (requester.getRole() != Role.ROLE_ADMIN && !fileEntity.getOwner().getId().equals(requester.getId())) {
            auditService.logAction(requester, "DOWNLOAD_DENIED",
                    "Attempted to download file: " + fileEntity.getFileName());
            throw new RuntimeException("Unauthorized Access");
        }

        Path path = Paths.get(fileEntity.getEncryptedPath());
        byte[] encryptedBytes = Files.readAllBytes(path);
        byte[] decryptedBytes = decrypt(encryptedBytes);

        auditService.logAction(requester, "DOWNLOAD", "Downloaded file: " + fileEntity.getFileName());
        return decryptedBytes;
    }

    public List<FileEntity> getAllFiles(User requester) {
        if (requester.getRole() == Role.ROLE_ADMIN) {
            return fileRepository.findAll();
        }
        return fileRepository.findByOwner(requester);
    }

    public FileEntity getFile(Long id) {
        return fileRepository.findById(id).orElse(null);
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
