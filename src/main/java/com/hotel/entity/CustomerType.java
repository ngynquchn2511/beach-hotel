package com.hotel.entity;

public enum CustomerType {
    NEW,
    REGULAR,
    VIP;

    // Khach than thiet: so lan luu tru hoan tat (da tra phong) toi thieu de tu dong len hang.
    // Chi tu nang hang, khong tu ha (admin van chinh tay duoc o trang Khach hang)
    public static final int REGULAR_MIN_STAYS = 2;
    public static final int VIP_MIN_STAYS = 5;

    public String getVietnameseLabel() {
        return switch (this) {
            case NEW -> "Khách lần đầu";
            case REGULAR -> "Khách quen";
            case VIP -> "Khách VIP";
        };
    }

    // Hang dat duoc voi so lan luu tru hoan tat
    public static CustomerType forCompletedStays(long stays) {
        if (stays >= VIP_MIN_STAYS) {
            return VIP;
        }
        return stays >= REGULAR_MIN_STAYS ? REGULAR : NEW;
    }

    // Hang tiep theo (null neu da VIP) va so lan luu tru can co
    public CustomerType next() {
        return this == VIP ? null : values()[ordinal() + 1];
    }

    public int minStays() {
        return switch (this) {
            case NEW -> 0;
            case REGULAR -> REGULAR_MIN_STAYS;
            case VIP -> VIP_MIN_STAYS;
        };
    }
}
