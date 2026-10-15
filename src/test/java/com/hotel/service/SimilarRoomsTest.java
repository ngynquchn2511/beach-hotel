package com.hotel.service;

import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomImageRepository;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// "Phong tuong tu" o trang chi tiet phong
@ExtendWith(MockitoExtension.class)
class SimilarRoomsTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeService roomTypeService;
    @Mock private BookingRepository bookingRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private RoomImageRepository roomImageRepository;

    @InjectMocks
    private RoomService roomService;

    private final RoomType deluxe = RoomType.builder().id(1L).name("Deluxe").maxGuests(2).build();
    private final RoomType family = RoomType.builder().id(2L).name("Family").maxGuests(4).build();

    private Room room(long id, String number, RoomType type, long price, RoomStatus status) {
        return Room.builder().id(id).roomNumber(number).roomType(type).price(BigDecimal.valueOf(price)).status(status).build();
    }

    @Test
    void noDates_sameTypeFirstThenClosestPrice_excludesSelfAndMaintenance() {
        Room current = room(1, "101", deluxe, 1_000_000, RoomStatus.AVAILABLE);
        Room sameTypeFar = room(2, "102", deluxe, 1_500_000, RoomStatus.BOOKED);
        Room sameTypeNear = room(3, "103", deluxe, 1_100_000, RoomStatus.AVAILABLE);
        Room otherNear = room(4, "201", family, 1_050_000, RoomStatus.AVAILABLE);
        Room otherFar = room(5, "202", family, 3_000_000, RoomStatus.AVAILABLE);
        Room maintenance = room(6, "104", deluxe, 1_000_000, RoomStatus.MAINTENANCE);
        when(roomRepository.findAll()).thenReturn(List.of(current, sameTypeFar, sameTypeNear, otherNear, otherFar, maintenance));

        List<Room> similar = roomService.findSimilarRooms(current, null, null, null, 3);

        assertEquals(List.of(sameTypeNear, sameTypeFar, otherNear), similar);
        verify(roomRepository, never()).findAvailableRooms(any(), any(), any(), any());
    }

    @Test
    void noDates_filtersByGuestCount() {
        Room current = room(1, "101", deluxe, 1_000_000, RoomStatus.AVAILABLE);
        Room otherDeluxe = room(2, "102", deluxe, 1_000_000, RoomStatus.AVAILABLE);
        Room familyRoom = room(3, "201", family, 2_000_000, RoomStatus.AVAILABLE);
        when(roomRepository.findAll()).thenReturn(List.of(current, otherDeluxe, familyRoom));

        assertEquals(List.of(familyRoom), roomService.findSimilarRooms(current, null, null, 3, 3));
    }

    @Test
    void validDates_usesAvailabilityQuery() {
        LocalDate in = LocalDate.now().plusDays(5);
        LocalDate out = in.plusDays(2);
        Room current = room(1, "101", deluxe, 1_000_000, RoomStatus.AVAILABLE);
        Room free = room(3, "103", deluxe, 1_100_000, RoomStatus.AVAILABLE);
        when(roomRepository.findAvailableRooms(in, out, null, 2)).thenReturn(List.of(current, free));

        assertEquals(List.of(free), roomService.findSimilarRooms(current, in, out, 2, 3));
        verify(roomRepository, never()).findAll();
    }

    @Test
    void pastOrInvalidDates_fallBackToAllRooms() {
        Room current = room(1, "101", deluxe, 1_000_000, RoomStatus.AVAILABLE);
        Room other = room(2, "102", deluxe, 1_000_000, RoomStatus.AVAILABLE);
        when(roomRepository.findAll()).thenReturn(List.of(current, other));
        LocalDate past = LocalDate.now().minusDays(3);

        assertEquals(List.of(other), roomService.findSimilarRooms(current, past, past.plusDays(1), null, 3));
        LocalDate future = LocalDate.now().plusDays(3);
        assertEquals(List.of(other), roomService.findSimilarRooms(current, future, future, null, 3));
        verify(roomRepository, never()).findAvailableRooms(any(), any(), any(), any());
    }
}
