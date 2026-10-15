package com.hotel.repository;

import com.hotel.entity.RoomAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RoomAlertRepository extends JpaRepository<RoomAlert, Long> {

    // Yeu cau dang cho (chua bao) va ngay nhan phong chua qua - nap san phong de kiem tra / gui email
    @Query("select a from RoomAlert a join fetch a.room r join fetch r.roomType "
            + "where a.notifiedAt is null and a.checkIn >= :today order by a.createdAt")
    List<RoomAlert> findPending(@Param("today") LocalDate today);

    // Viet tay: ten ham kieu "...CheckIn..." bi Spring Data hieu nham "In" la toan tu
    @Query("select count(a) > 0 from RoomAlert a where a.room.id = :roomId and lower(a.email) = lower(:email) "
            + "and a.checkIn = :checkIn and a.checkOut = :checkOut and a.notifiedAt is null")
    boolean existsPending(@Param("roomId") Long roomId, @Param("email") String email,
                          @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    @Query("select count(a) from RoomAlert a where lower(a.email) = lower(:email) and a.notifiedAt is null and a.checkIn >= :today")
    long countPendingForEmail(@Param("email") String email, @Param("today") LocalDate today);

    // Ngay nhan phong da qua ma phong chua trong lai -> bo yeu cau
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RoomAlert a where a.notifiedAt is null and a.checkIn < :today")
    int deleteExpired(@Param("today") LocalDate today);
}
