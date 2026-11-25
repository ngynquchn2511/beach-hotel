package com.hotel.repository;

import com.hotel.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByStartedAtGreaterThanEqualAndStartedAtLessThan(LocalDateTime from, LocalDateTime to);

    // Gop y chua hai long (moi nhat truoc)
    List<ChatSession> findByOutcomeOrderByFeedbackAtDesc(String outcome);

    long countByOutcomeAndHandledAtIsNull(String outcome);
}
