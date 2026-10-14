package com.hotel.service;

import com.hotel.entity.Combo;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ComboRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComboServiceTest {

    @Mock private ComboRepository comboRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private ComboService comboService;

    @Test
    void findActive_delegatesToRepositoryActiveTrue() {
        Combo c = Combo.builder().id(1L).name("Combo A").active(true).build();
        when(comboRepository.findByActiveTrue()).thenReturn(List.of(c));

        List<Combo> result = comboService.findActive();

        assertEquals(1, result.size());
        verify(comboRepository, times(1)).findByActiveTrue();
    }

    @Test
    void toggleActive_fromActiveToInactive() {
        Combo c = Combo.builder().id(1L).name("Combo A").active(true).build();
        when(comboRepository.findById(1L)).thenReturn(Optional.of(c));
        when(comboRepository.save(any(Combo.class))).thenAnswer(inv -> inv.getArgument(0));

        comboService.toggleActive(1L);

        assertFalse(c.isActive());
    }

    @Test
    void toggleActive_fromInactiveToActive() {
        Combo c = Combo.builder().id(1L).name("Combo A").active(false).build();
        when(comboRepository.findById(1L)).thenReturn(Optional.of(c));
        when(comboRepository.save(any(Combo.class))).thenAnswer(inv -> inv.getArgument(0));

        comboService.toggleActive(1L);

        assertTrue(c.isActive());
    }

    @Test
    void findById_notFound_throws() {
        when(comboRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> comboService.findById(99L));
    }
}
