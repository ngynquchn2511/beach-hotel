package com.hotel.entity;

// Trang thai don dich vu them (tour lan, dua don san bay) gan voi 1 don dat phong
public enum ServiceStatus {
    // Khach vua dat, cho le tan goi xac nhan (chua tinh tien)
    PENDING,
    // Le tan da xac nhan - tien dich vu duoc ghi thanh phu phi cua don phong
    CONFIRMED,
    CANCELLED;

    public String getVietnameseLabel() {
        return switch (this) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case CANCELLED -> "Đã hủy";
        };
    }
}
