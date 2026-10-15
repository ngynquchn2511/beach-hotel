package com.hotel.repository;

import com.hotel.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    long countByHandledFalse();

    // Chong spam: so tin 1 email da gui trong khoang thoi gian gan day
    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, LocalDateTime since);
}
