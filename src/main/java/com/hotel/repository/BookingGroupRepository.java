package com.hotel.repository;

import com.hotel.entity.BookingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingGroupRepository extends JpaRepository<BookingGroup, Long> {
}
