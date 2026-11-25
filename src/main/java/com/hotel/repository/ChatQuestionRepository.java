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

    // Tong chi phi AI (phan trieu USD) tu thoi diem from
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(q.aiCostMicro), 0) from ChatQuestion q where q.askedAt >= :from and q.aiCostMicro is not null")
    long sumAiCostSince(@org.springframework.data.repository.query.Param("from") LocalDateTime from);

    // So cau AI da tra loi cho 1 khach tu thoi diem from
    @org.springframework.data.jpa.repository.Query("select count(q) from ChatQuestion q where q.session.customer.id = :customerId and q.askedAt >= :from and q.aiStatus = 'AI'")
    long countAiAnswersSince(@org.springframework.data.repository.query.Param("customerId") Long customerId,
                             @org.springframework.data.repository.query.Param("from") LocalDateTime from);
}
