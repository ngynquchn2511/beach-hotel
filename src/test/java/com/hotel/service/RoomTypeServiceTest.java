package com.hotel.service;

import com.hotel.dto.RoomTypeRequest;
import com.hotel.entity.Room;
import com.hotel.entity.RoomType;
import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomTypeServiceTest {

    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomRepository roomRepository;

    @InjectMocks
    private RoomTypeService roomTypeService;

    @Test
    void delete_stillInUse_throws() {
        RoomType rt = RoomType.builder().id(1L).build();
        Room r = Room.builder().id(1L).roomType(rt).build();
        when(roomRepository.findAll()).thenReturn(List.of(r));

        assertThrows(BusinessException.class, () -> roomTypeService.delete(1L));
        verify(roomTypeRepository, never()).deleteById(any());
    }

    @Test
    void delete_notInUse_success() {
        when(roomRepository.findAll()).thenReturn(List.of());

        roomTypeService.delete(1L);

        verify(roomTypeRepository, times(1)).deleteById(1L);
    }

    @Test
    void create_success_mapsAllFields() {
        RoomTypeRequest req = new RoomTypeRequest();
        req.setName("Phong Suite");
        req.setBasePrice(BigDecimal.valueOf(1200000));
        req.setMaxGuests(4);
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomType result = roomTypeService.create(req);

        assertEquals("Phong Suite", result.getName());
        assertEquals(4, result.getMaxGuests());
    }

    @Test
    void findById_notFound_throws() {
        when(roomTypeRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> roomTypeService.findById(99L));
    }
}
