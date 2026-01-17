package com.securefilesharing.controller;

import com.securefilesharing.entity.FileEntity;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile[] files) {
        try {
            User user = getCurrentUser();
            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest().body("No file(s) provided");
            }

            List<Map<String, Object>> uploaded = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                FileEntity saved = fileService.uploadFile(file, user);
                uploaded.add(Map.of(
                        "id", saved.getId(),
                        "fileName", saved.getFileName()
                ));
            }

            if (uploaded.isEmpty()) {
                return ResponseEntity.badRequest().body("All provided files were empty");
            }

            return ResponseEntity.ok(Map.of(
                    "message", "File(s) uploaded successfully",
                    "count", uploaded.size(),
                    "files", uploaded
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload the file: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<FileEntity>> getListFiles() {
        User user = getCurrentUser();
        List<FileEntity> files = fileService.getAllFiles(user);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            byte[] data = fileService.downloadFile(id, user);
            FileEntity fileInfo = fileService.getFile(id);

            ByteArrayResource resource = new ByteArrayResource(data);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileInfo.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
