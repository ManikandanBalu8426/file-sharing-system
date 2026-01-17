package com.securefilesharing.dto;

import com.securefilesharing.entity.AccessType;

public class CreateAccessRequestDto {
    private String purpose;
    private AccessType accessType;

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(AccessType accessType) {
        this.accessType = accessType;
    }
}
