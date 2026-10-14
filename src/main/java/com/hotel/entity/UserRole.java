package com.hotel.entity;

public enum UserRole {
    CUSTOMER,
    STAFF,
    ADMIN;

    public String getVietnameseLabel() {
        return switch (this) {
            case CUSTOMER -> "Khách hàng";
            case STAFF -> "Nhân viên";
            case ADMIN -> "Quản trị viên";
        };
    }
}
