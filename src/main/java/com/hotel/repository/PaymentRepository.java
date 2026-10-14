package com.hotel.repository;

import com.hotel.entity.Payment;
import com.hotel.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);

    List<Payment> findByStatusAndPaymentDateBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);
}
