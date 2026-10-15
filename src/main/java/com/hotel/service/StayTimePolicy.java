package com.hotel.service;

import com.hotel.entity.Booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;

// Khung gio tieu chuan cua resort va phu phi nhan phong som / tra phong muon (tinh theo gia trung binh 1 dem cua don):
// - Nhan som toi da 3 tieng (tu 11:00): 30%; som hon: 50%
// - Tra muon den 15:00: 30%; den 18:00: 50%; sau 18:00: 100%
// Trong khung (nhan tu 14:00, tra truoc 12:00) khach tu chon, khong mat phi
public final class StayTimePolicy {

    public static final LocalTime STANDARD_CHECK_IN = LocalTime.of(14, 0);
    public static final LocalTime STANDARD_CHECK_OUT = LocalTime.of(12, 0);

    private StayTimePolicy() {
    }

    public static boolean isEarlyCheckIn(LocalTime time) {
        return time != null && time.isBefore(STANDARD_CHECK_IN);
    }

    public static boolean isLateCheckOut(LocalTime time) {
        return time != null && time.isAfter(STANDARD_CHECK_OUT);
    }

    public static int earlyPercent(LocalTime checkIn) {
        if (!isEarlyCheckIn(checkIn)) {
            return 0;
        }
        return Duration.between(checkIn, STANDARD_CHECK_IN).toMinutes() <= 180 ? 30 : 50;
    }

    public static int latePercent(LocalTime checkOut) {
        if (!isLateCheckOut(checkOut)) {
            return 0;
        }
        long minutes = Duration.between(STANDARD_CHECK_OUT, checkOut).toMinutes();
        return minutes <= 180 ? 30 : (minutes <= 360 ? 50 : 100);
    }

    // Gio chon duoc khi dat / sua gio (moi 30 phut): nhan 14:00 - 23:30, tra 00:00 - 12:00
    public static java.util.List<LocalTime> checkInOptions() {
        return range(STANDARD_CHECK_IN, LocalTime.of(23, 30));
    }

    public static java.util.List<LocalTime> checkOutOptions() {
        return range(LocalTime.MIDNIGHT, STANDARD_CHECK_OUT);
    }

    // Gio xin nhan som (06:00 - 13:30) / tra muon (12:30 - 22:00)
    public static java.util.List<LocalTime> earlyOptions() {
        return range(LocalTime.of(6, 0), LocalTime.of(13, 30));
    }

    public static java.util.List<LocalTime> lateOptions() {
        return range(LocalTime.of(12, 30), LocalTime.of(22, 0));
    }

    private static java.util.List<LocalTime> range(LocalTime from, LocalTime to) {
        java.util.List<LocalTime> list = new java.util.ArrayList<>();
        for (LocalTime t = from; !t.isAfter(to); t = t.plusMinutes(30)) {
            list.add(t);
            if (t.equals(LocalTime.of(23, 30))) {
                break;
            }
        }
        return list;
    }

    // Gia trung binh 1 dem cua don (tien phong da chot / so dem)
    public static BigDecimal nightlyRate(Booking booking) {
        long nights = Math.max(1, booking.getNights());
        return booking.getEffectiveRoomAmount().divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP);
    }

    // Phu phi lam tron toi 1.000d
    public static BigDecimal fee(Booking booking, int percent) {
        if (percent <= 0) {
            return BigDecimal.ZERO;
        }
        return nightlyRate(booking).multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100_000), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1000));
    }
}
