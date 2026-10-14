package com.hotel.entity;

// Loai phu phi phat sinh khi khach o (tinh luc tra phong)
public enum ChargeType {
    MINIBAR,
    LAUNDRY,
    DAMAGE,
    LATE_CHECKOUT,
    OTHER;

    public String getVietnameseLabel() {
        return switch (this) {
            case MINIBAR -> "Minibar";
            case LAUNDRY -> "Giặt ủi";
            case DAMAGE -> "Hư hỏng / mất đồ";
            case LATE_CHECKOUT -> "Trả phòng muộn";
            case OTHER -> "Khác";
        };
    }
}
