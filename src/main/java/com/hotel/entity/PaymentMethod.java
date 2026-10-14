package com.hotel.entity;

public enum PaymentMethod {
    CASH,
    BANK_TRANSFER,
    // Thanh toan bang the - hien chua ho tro, chi hien thi tren man hinh check-out
    CARD,
    ONLINE;

    public String getVietnameseLabel() {
        return switch (this) {
            case CASH -> "Tiền mặt";
            case BANK_TRANSFER -> "Chuyển khoản ngân hàng";
            case CARD -> "Thẻ ngân hàng";
            case ONLINE -> "Thanh toán online";
        };
    }
}
