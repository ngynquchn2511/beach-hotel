package com.hotel.entity;

// Loai xe dua don - so khach toi da khong tinh tai xe
public enum VehicleType {
    CAR_4(3),
    CAR_7(6),
    VAN_16(15);

    private final int maxPassengers;

    VehicleType(int maxPassengers) {
        this.maxPassengers = maxPassengers;
    }

    public int getMaxPassengers() {
        return maxPassengers;
    }

    public String getVietnameseLabel() {
        return switch (this) {
            case CAR_4 -> "Xe 4 chỗ";
            case CAR_7 -> "Xe 7 chỗ";
            case VAN_16 -> "Xe 16 chỗ";
        };
    }
}
