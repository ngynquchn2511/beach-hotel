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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// "Phong tuong tu" o trang chi tiet phong: service chi quyet dinh co loc theo ngay hay khong,
// loc + sap xep nam trong truy van (kiem tra o SimilarRoomsIT)
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
    private final Room current = Room.builder().id(1L).roomNumber("101").roomType(deluxe)
            .price(BigDecimal.valueOf(1_000_000)).status(RoomStatus.AVAILABLE).build();

    @Test
    void validDates_filterByThoseDates() {
        LocalDate in = LocalDate.now().plusDays(5);
        LocalDate out = in.plusDays(2);
        Room free = Room.builder().id(3L).roomNumber("103").roomType(deluxe).build();
        when(roomRepository.findSimilarRooms(1L, 1L, BigDecimal.valueOf(1_000_000), 2, true, in, out, PageRequest.of(0, 3)))
                .thenReturn(List.of(free));

        assertEquals(List.of(free), roomService.findSimilarRooms(current, in, out, 2, 3));
    }

    @Test
    void noOrInvalidDates_doNotFilterByDates() {
        LocalDate past = LocalDate.now().minusDays(3);
        LocalDate future = LocalDate.now().plusDays(3);

        roomService.findSimilarRooms(current, null, null, null, 3);
        roomService.findSimilarRooms(current, past, past.plusDays(1), null, 3);
        roomService.findSimilarRooms(current, future, future, null, 3);

        verify(roomRepository, times(3)).findSimilarRooms(eq(1L), eq(1L), any(), any(), eq(false), any(), any(), any());
        verify(roomRepository, never()).findSimilarRooms(any(), any(), any(), any(), eq(true), any(), any(), any());
    }

    @Test
    void isValidStay() {
        LocalDate today = LocalDate.now();
        assertTrue(RoomService.isValidStay(today, today.plusDays(1)));
        assertFalse(RoomService.isValidStay(null, today.plusDays(1)));
        assertFalse(RoomService.isValidStay(today.minusDays(1), today.plusDays(1)));
        assertFalse(RoomService.isValidStay(today.plusDays(2), today.plusDays(2)));
    }
}
