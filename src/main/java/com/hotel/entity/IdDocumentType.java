package com.hotel.entity;

// Loai giay to tuy than dung khi khai bao luu tru
public enum IdDocumentType {
    CCCD,
    PASSPORT,
    // Tre em chua co CCCD
    BIRTH_CERTIFICATE;

    public String getVietnameseLabel() {
        return switch (this) {
            case CCCD -> "Căn cước công dân";
            case PASSPORT -> "Hộ chiếu";
            case BIRTH_CERTIFICATE -> "Giấy khai sinh";
        };
    }
}
