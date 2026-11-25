package com.hotel.repository;

import com.hotel.entity.ChatQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ChatQuestionRepository extends JpaRepository<ChatQuestion, Long> {

    List<ChatQuestion> findByAskedAtGreaterThanEqualAndAskedAtLessThan(LocalDateTime from, LocalDateTime to);

    List<ChatQuestion> findBySessionIdOrderByAskedAtAsc(Long sessionId);

    List<ChatQuestion> findBySessionIdInOrderByAskedAtAsc(Collection<Long> sessionIds);
}
