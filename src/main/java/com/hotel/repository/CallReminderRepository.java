package com.hotel.repository;

import com.hotel.entity.CallReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CallReminderRepository extends JpaRepository<CallReminder, Long> {

    List<CallReminder> findByBookingIdInOrderByCalledAtAsc(Collection<Long> bookingIds);

    List<CallReminder> findByBookingIdOrderByCalledAtDesc(Long bookingId);

    @Query("select c from CallReminder c join fetch c.booking b join fetch b.room order by c.calledAt desc, c.id desc")
    List<CallReminder> findAllForHistory();
}
