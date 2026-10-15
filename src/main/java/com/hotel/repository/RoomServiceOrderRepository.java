package com.hotel.repository;

import com.hotel.entity.RoomServiceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RoomServiceOrderRepository extends JpaRepository<RoomServiceOrder, Long> {

    List<RoomServiceOrder> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    // Man hinh le tan: don cho giao (cu nhat len dau), nap san don phong + phong
    @Query("select o from RoomServiceOrder o join fetch o.booking b join fetch b.room "
            + "where o.status = com.hotel.entity.RoomServiceOrder.Status.PENDING order by o.createdAt")
    List<RoomServiceOrder> findPending();

    @Query("select o from RoomServiceOrder o join fetch o.booking b join fetch b.room "
            + "where o.status = com.hotel.entity.RoomServiceOrder.Status.DELIVERED and o.deliveredAt >= :from order by o.deliveredAt desc")
    List<RoomServiceOrder> findDeliveredSince(@Param("from") LocalDateTime from);

    long countByStatus(RoomServiceOrder.Status status);
}
