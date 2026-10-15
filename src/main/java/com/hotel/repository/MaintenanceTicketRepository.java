package com.hotel.repository;

import com.hotel.entity.MaintenanceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, Long> {

    // Phieu chua xong: khan len dau, roi cu nhat
    @Query("select t from MaintenanceTicket t join fetch t.room r join fetch r.roomType "
            + "where t.status <> com.hotel.entity.MaintenanceTicket.Status.DONE "
            + "order by case when t.priority = com.hotel.entity.MaintenanceTicket.Priority.URGENT then 0 else 1 end, t.createdAt")
    List<MaintenanceTicket> findOpen();

    @Query("select t from MaintenanceTicket t join fetch t.room where t.status = com.hotel.entity.MaintenanceTicket.Status.DONE "
            + "and t.resolvedAt >= :from order by t.resolvedAt desc")
    List<MaintenanceTicket> findResolvedSince(@Param("from") LocalDateTime from);

    List<MaintenanceTicket> findByRoomIdAndStatusNot(Long roomId, MaintenanceTicket.Status status);

    long countByStatusNot(MaintenanceTicket.Status status);
}
