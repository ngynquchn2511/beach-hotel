package com.hotel.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED;

    public String getVietnameseLabel() {
        return switch (this) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case CHECKED_IN -> "Đã nhận phòng";
            case CHECKED_OUT -> "Đã trả phòng";
            case CANCELLED -> "Đã hủy";
        };
    }

    // Class mau badge Bootstrap rieng cho tung trang thai de admin de phan biet
    public String getBadgeClass() {
        return switch (this) {
            case PENDING -> "bg-warning text-dark";
            case CONFIRMED -> "bg-info text-dark";
            case CHECKED_IN -> "bg-success";
            case CHECKED_OUT -> "bg-secondary";
            case CANCELLED -> "bg-danger";
        };
    }
}
