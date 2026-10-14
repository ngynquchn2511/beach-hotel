package com.hotel.entity;

public enum PaymentStatus {
    UNPAID,
    PAID,
    REFUNDED;

    public String getVietnameseLabel() {
        return switch (this) {
            case UNPAID -> "Chưa thanh toán";
            case PAID -> "Đã thanh toán";
            case REFUNDED -> "Đã hoàn tiền";
        };
    }
}
