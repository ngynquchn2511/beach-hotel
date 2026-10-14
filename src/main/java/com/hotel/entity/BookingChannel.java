package com.hotel.entity;

// Kenh dat phong - de bao cao doanh thu theo kenh
public enum BookingChannel {
    ONLINE,
    WALK_IN;

    public String getVietnameseLabel() {
        return switch (this) {
            case ONLINE -> "Đặt online";
            case WALK_IN -> "Tại quầy";
        };
    }
}
