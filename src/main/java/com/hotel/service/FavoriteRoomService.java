package com.hotel.service;

import com.hotel.entity.FavoriteRoom;
import com.hotel.entity.Room;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.FavoriteRoomRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Phong yeu thich cua khach co tai khoan: bam trai tim o trang phong de luu, xem lai o "Phong yeu thich"
@Service
public class FavoriteRoomService {

    // Gioi han de danh sach khong phinh vo han
    public static final int MAX_FAVORITES = 50;

    private final FavoriteRoomRepository favoriteRoomRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public FavoriteRoomService(FavoriteRoomRepository favoriteRoomRepository, RoomRepository roomRepository,
                               UserRepository userRepository) {
        this.favoriteRoomRepository = favoriteRoomRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    // Bat / tat yeu thich. Tra ve true neu sau khi bam phong dang nam trong danh sach yeu thich
    @Transactional
    public boolean toggle(Long userId, Long roomId) {
        var existing = favoriteRoomRepository.findByUserIdAndRoomId(userId, roomId);
        if (existing.isPresent()) {
            favoriteRoomRepository.delete(existing.get());
            return false;
        }
        Room room = roomRepository.findById(roomId).orElseThrow(() -> BusinessException.of("favorite.error.roomNotFound"));
        if (favoriteRoomRepository.findRoomIdsByUserId(userId).size() >= MAX_FAVORITES) {
            throw BusinessException.of("favorite.error.tooMany", MAX_FAVORITES);
        }
        User user = userRepository.getReferenceById(userId);
        favoriteRoomRepository.save(FavoriteRoom.builder().user(user).room(room).build());
        return true;
    }

    @Transactional(readOnly = true)
    public List<Room> rooms(Long userId) {
        return favoriteRoomRepository.findForUser(userId).stream().map(FavoriteRoom::getRoom).toList();
    }

    // Id cac phong da yeu thich - trang danh sach phong dung de to do trai tim. null user = khach chua dang nhap
    @Transactional(readOnly = true)
    public Set<Long> roomIds(Long userId) {
        return userId == null ? Set.of() : new HashSet<>(favoriteRoomRepository.findRoomIdsByUserId(userId));
    }
}
