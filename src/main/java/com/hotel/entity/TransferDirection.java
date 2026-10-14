package com.hotel.entity;

public enum TransferDirection {
    // Don khach tu san bay ve resort (ngay nhan phong)
    PICKUP,
    // Tien khach tu resort ra san bay (ngay tra phong)
    DROPOFF;

    public String getVietnameseLabel() {
        return this == PICKUP ? "Đón từ sân bay về resort" : "Tiễn từ resort ra sân bay";
    }
}
