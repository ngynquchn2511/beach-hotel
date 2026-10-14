package com.hotel.entity;

public enum RoomStatus {
    AVAILABLE,
    BOOKED,
    OCCUPIED,
    MAINTENANCE,
    // Phong vua checkout xong, can nhan vien/admin don dep va xac nhan truoc khi cho dat lai
    NOT_READY;

    public String getVietnameseLabel() {
        return switch (this) {
            case AVAILABLE -> "Còn trống";
            case BOOKED -> "Đã được đặt";
            case OCCUPIED -> "Đang sử dụng";
            case MAINTENANCE -> "Đang bảo trì";
            case NOT_READY -> "Chưa sẵn sàng (đang dọn dẹp)";
        };
    }
}
