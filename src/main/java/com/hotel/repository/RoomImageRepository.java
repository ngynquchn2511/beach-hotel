package com.hotel.repository;

import com.hotel.entity.RoomImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomImageRepository extends JpaRepository<RoomImage, Long> {

    List<RoomImage> findByRoomIdOrderById(Long roomId);

    long countByRoomId(Long roomId);

    void deleteByRoomId(Long roomId);
}
