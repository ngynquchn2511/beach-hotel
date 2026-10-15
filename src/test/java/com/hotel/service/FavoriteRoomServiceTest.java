package com.hotel.service;

import com.hotel.entity.FavoriteRoom;
import com.hotel.entity.Room;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.FavoriteRoomRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteRoomServiceTest {

    @Mock private FavoriteRoomRepository favoriteRoomRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private FavoriteRoomService service;

    @Test
    void toggle_notYetFavorite_savesAndReturnsTrue() {
        Room room = Room.builder().id(3L).build();
        User user = User.builder().id(1L).build();
        when(favoriteRoomRepository.findByUserIdAndRoomId(1L, 3L)).thenReturn(Optional.empty());
        when(roomRepository.findById(3L)).thenReturn(Optional.of(room));
        when(favoriteRoomRepository.findRoomIdsByUserId(1L)).thenReturn(Collections.emptyList());
        when(userRepository.getReferenceById(1L)).thenReturn(user);

        assertTrue(service.toggle(1L, 3L));

        ArgumentCaptor<FavoriteRoom> saved = ArgumentCaptor.forClass(FavoriteRoom.class);
        verify(favoriteRoomRepository).save(saved.capture());
        assertSame(room, saved.getValue().getRoom());
        assertSame(user, saved.getValue().getUser());
    }

    @Test
    void toggle_alreadyFavorite_deletesAndReturnsFalse() {
        FavoriteRoom existing = FavoriteRoom.builder().id(9L).build();
        when(favoriteRoomRepository.findByUserIdAndRoomId(1L, 3L)).thenReturn(Optional.of(existing));

        assertFalse(service.toggle(1L, 3L));

        verify(favoriteRoomRepository).delete(existing);
        verify(favoriteRoomRepository, never()).save(any());
    }

    @Test
    void toggle_unknownRoom_throws() {
        when(favoriteRoomRepository.findByUserIdAndRoomId(1L, 99L)).thenReturn(Optional.empty());
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.toggle(1L, 99L));
        assertEquals(BusinessException.vietnamese("favorite.error.roomNotFound"), ex.getMessage());
    }

    @Test
    void toggle_atLimit_throws() {
        when(favoriteRoomRepository.findByUserIdAndRoomId(1L, 3L)).thenReturn(Optional.empty());
        when(roomRepository.findById(3L)).thenReturn(Optional.of(Room.builder().id(3L).build()));
        List<Long> full = LongStream.rangeClosed(100, 99 + FavoriteRoomService.MAX_FAVORITES).boxed().toList();
        when(favoriteRoomRepository.findRoomIdsByUserId(1L)).thenReturn(full);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.toggle(1L, 3L));
        assertTrue(ex.getMessage().contains(String.valueOf(FavoriteRoomService.MAX_FAVORITES)));
        verify(favoriteRoomRepository, never()).save(any());
    }

    @Test
    void roomIds_anonymous_isEmptyWithoutQuery() {
        assertEquals(Set.of(), service.roomIds(null));
        verifyNoInteractions(favoriteRoomRepository);
    }
}
