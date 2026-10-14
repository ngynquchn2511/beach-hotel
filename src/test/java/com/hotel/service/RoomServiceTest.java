package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeService roomTypeService;
    @Mock private BookingRepository bookingRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private RoomImageRepository roomImageRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void searchAvailableRooms_checkInInPast_throws() {
        LocalDate past = LocalDate.now().minusDays(1);
        assertThrows(BusinessException.class,
                () -> roomService.searchAvailableRooms(past, past.plusDays(2), 2, null));
    }

    @Test
    void searchAvailableRooms_checkOutNotAfterCheckIn_throws() {
        LocalDate d = LocalDate.now().plusDays(1);
        assertThrows(BusinessException.class, () -> roomService.searchAvailableRooms(d, d, 2, null));
    }

    @Test
    void searchAvailableRooms_missingDates_throws() {
        assertThrows(BusinessException.class, () -> roomService.searchAvailableRooms(null, null, 2, null));
    }

    @Test
    void isAvailableForDates_maintenanceRoom_false() {
        Room room = Room.builder().id(1L).status(RoomStatus.MAINTENANCE).build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        boolean result = roomService.isAvailableForDates(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

        assertFalse(result);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void isAvailableForDates_overlapExists_false() {
        Room room = Room.builder().id(1L).status(RoomStatus.AVAILABLE).build();
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(1L, in, out)).thenReturn(true);

        assertFalse(roomService.isAvailableForDates(1L, in, out));
    }

    @Test
    void isAvailableForDates_noOverlapAndAvailable_true() {
        Room room = Room.builder().id(1L).status(RoomStatus.AVAILABLE).build();
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(1L, in, out)).thenReturn(false);

        assertTrue(roomService.isAvailableForDates(1L, in, out));
    }

    @Test
    void create_duplicateRoomNumber_throws() {
        var req = new com.hotel.dto.RoomRequest();
        req.setRoomNumber("101");
        req.setRoomTypeId(1L);
        req.setPrice(BigDecimal.valueOf(300000));
        when(roomRepository.existsByRoomNumber("101")).thenReturn(true);

        assertThrows(BusinessException.class, () -> roomService.create(req, List.of()));
        verify(roomRepository, never()).save(any());
    }

    @Test
    void create_success_defaultsStatusAvailable() {
        var req = new com.hotel.dto.RoomRequest();
        req.setRoomNumber("102");
        req.setRoomTypeId(1L);
        req.setPrice(BigDecimal.valueOf(300000));
        RoomType rt = RoomType.builder().id(1L).name("Phong Doi").build();
        when(roomRepository.existsByRoomNumber("102")).thenReturn(false);
        when(roomTypeService.findById(1L)).thenReturn(rt);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        Room result = roomService.create(req, List.of());

        assertEquals(RoomStatus.AVAILABLE, result.getStatus());
        assertEquals("102", result.getRoomNumber());
    }

    @Test
    void delete_withExistingBookings_throws() {
        Room room = Room.builder().id(1L).build();
        Booking b = Booking.builder().id(1L).room(room).build();
        when(bookingRepository.findAll()).thenReturn(List.of(b));

        assertThrows(BusinessException.class, () -> roomService.delete(1L));
        verify(roomRepository, never()).deleteById(any());
    }

    @Test
    void markReady_wrongStatus_throws() {
        Room room = Room.builder().id(1L).status(RoomStatus.AVAILABLE).build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        assertThrows(BusinessException.class, () -> roomService.markReady(1L));
    }

    @Test
    void markReady_fromNotReady_setsAvailable() {
        Room room = Room.builder().id(1L).status(RoomStatus.NOT_READY).build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.markReady(1L);

        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    }

    @Test
    void countByStatus_countsOnlyMatching() {
        Room r1 = Room.builder().id(1L).status(RoomStatus.AVAILABLE).build();
        Room r2 = Room.builder().id(2L).status(RoomStatus.MAINTENANCE).build();
        Room r3 = Room.builder().id(3L).status(RoomStatus.AVAILABLE).build();
        when(roomRepository.findAll()).thenReturn(List.of(r1, r2, r3));

        assertEquals(2, roomService.countByStatus(RoomStatus.AVAILABLE));
        assertEquals(1, roomService.countByStatus(RoomStatus.MAINTENANCE));
    }
}
