package com.hotel.util;

import com.hotel.entity.Room;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomListingTest {

    private Room room(String number, long price) {
        return Room.builder().roomNumber(number).price(BigDecimal.valueOf(price)).build();
    }

    private final List<Room> rooms = List.of(room("101", 650_000), room("201", 1_100_000), room("202", 1_500_000),
            room("301", 1_600_000), room("501", 2_800_000), room("B01", 3_200_000), room("601", 5_500_000));

    @Test
    void priceRanges_coverEveryPriceExactlyOnce() {
        assertThat(RoomListing.apply(rooms, "r1", null)).extracting(Room::getRoomNumber).containsExactly("101");
        assertThat(RoomListing.apply(rooms, "r2", null)).extracting(Room::getRoomNumber).containsExactly("201", "202");
        assertThat(RoomListing.apply(rooms, "R3", null)).extracting(Room::getRoomNumber).containsExactly("301", "501");
        assertThat(RoomListing.apply(rooms, "r4", null)).extracting(Room::getRoomNumber).containsExactly("B01", "601");
        // Tham so la -> khong loc
        assertThat(RoomListing.apply(rooms, "r9", null)).hasSize(rooms.size());
        assertThat(RoomListing.apply(rooms, null, null)).hasSize(rooms.size());
    }

    @Test
    void sortByPrice() {
        assertThat(RoomListing.apply(List.of(rooms.get(4), rooms.get(0), rooms.get(2)), null, "price-asc"))
                .extracting(Room::getRoomNumber).containsExactly("101", "202", "501");
        assertThat(RoomListing.apply(rooms, "r4", "price-desc"))
                .extracting(Room::getRoomNumber).containsExactly("601", "B01");
    }
}
