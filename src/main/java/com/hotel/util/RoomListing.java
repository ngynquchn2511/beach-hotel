package com.hotel.util;

import com.hotel.entity.Room;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// Loc theo khoang gia + sap xep danh sach phong o phia server (truoc day lam bang JS tren trinh duyet,
// nhung da phan trang thi JS chi thay duoc 1 trang -> phai loc / sap xep truoc roi moi cat trang)
public final class RoomListing {

    // Khoang gia tren thanh ben (gia tri tham so ?price=r1..r4), bien tinh theo VND / dem
    public enum PriceRange {
        R1(0, 999_999),
        R2(1_000_000, 1_500_000),
        R3(1_500_001, 3_000_000),
        R4(3_000_001, Long.MAX_VALUE);

        private final long min;
        private final long max;

        PriceRange(long min, long max) {
            this.min = min;
            this.max = max;
        }

        public String param() {
            return name().toLowerCase();
        }

        boolean matches(BigDecimal price) {
            return price.compareTo(BigDecimal.valueOf(min)) >= 0 && price.compareTo(BigDecimal.valueOf(max)) <= 0;
        }

        // Gia tri la / bo trong -> khong loc
        public static PriceRange fromParam(String value) {
            for (PriceRange range : values()) {
                if (range.param().equalsIgnoreCase(value)) {
                    return range;
                }
            }
            return null;
        }
    }

    public static final String SORT_PRICE_ASC = "price-asc";
    public static final String SORT_PRICE_DESC = "price-desc";

    private RoomListing() {
    }

    public static List<Room> apply(List<Room> rooms, String price, String sort) {
        PriceRange range = PriceRange.fromParam(price);
        Comparator<Room> order = switch (sort == null ? "" : sort) {
            case SORT_PRICE_ASC -> Comparator.comparing(Room::getPrice).thenComparing(Room::getRoomNumber);
            case SORT_PRICE_DESC -> Comparator.comparing(Room::getPrice).reversed().thenComparing(Room::getRoomNumber);
            default -> null;
        };
        var stream = rooms.stream().filter(r -> range == null || range.matches(r.getPrice()));
        return order == null ? stream.toList() : stream.sorted(order).toList();
    }
}
