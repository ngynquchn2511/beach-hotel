package com.hotel.entity;

public enum Gender {
    MALE,
    FEMALE;

    public String getVietnameseLabel() {
        return switch (this) {
            case MALE -> "Nam";
            case FEMALE -> "Nữ";
        };
    }
}
