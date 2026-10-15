package com.hotel.repository;

import com.hotel.entity.RoomServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomServiceItemRepository extends JpaRepository<RoomServiceItem, Long> {

    List<RoomServiceItem> findByActiveTrueOrderByCategoryAscNameAsc();

    List<RoomServiceItem> findAllByOrderByCategoryAscNameAsc();
}
