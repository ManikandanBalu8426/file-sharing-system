package com.securefilesharing.dto;

import java.time.LocalDateTime;

/**
 * DTO for file metadata viewing by auditors.
 * Contains ONLY metadata - NO file path or content access.
 */
public class AuditFileMetadataDto {

    private Long fileId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String uploadedBy;
    private LocalDateTime uploadDate;
    private String visibilityLevel;
    private String category;
    private String purpose;

    public AuditFileMetadataDto() {
    }

    public AuditFileMetadataDto(Long fileId, String fileName, Long fileSize, String fileType,
                                String uploadedBy, LocalDateTime uploadDate, String visibilityLevel,
                                String category, String purpose) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.uploadedBy = uploadedBy;
        this.uploadDate = uploadDate;
        this.visibilityLevel = visibilityLevel;
        this.category = category;
        this.purpose = purpose;
    }

    // Getters and Setters

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getVisibilityLevel() {
        return visibilityLevel;
    }

    public void setVisibilityLevel(String visibilityLevel) {
        this.visibilityLevel = visibilityLevel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
