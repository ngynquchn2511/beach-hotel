package com.hotel.entity;

public enum DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT;

    public String getVietnameseLabel() {
        return this == PERCENTAGE ? "Phần trăm" : "Số tiền cố định";
    }
}
