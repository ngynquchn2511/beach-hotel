package com.hotel.entity;

public enum CustomerType {
    NEW,
    REGULAR,
    VIP;

    public String getVietnameseLabel() {
        return switch (this) {
            case NEW -> "Khách lần đầu";
            case REGULAR -> "Khách quen";
            case VIP -> "Khách VIP";
        };
    }
}
