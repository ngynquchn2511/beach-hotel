package com.hotel.entity;

// Yeu cau nhan phong som / tra phong muon cua khach (ngoai khung 14:00 - 12:00, co phu phi, le tan duyet)
public enum StayTimeRequestStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public String getVietnameseLabel() {
        return switch (this) {
            case PENDING -> "Chờ duyệt";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Từ chối";
        };
    }
}
