package com.hotel.repository;

import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByStatus(RoomStatus status);

    List<Room> findByRoomTypeId(Long roomTypeId);

    boolean existsByRoomNumber(String roomNumber);

    // Khoa dong phong (SELECT ... FOR UPDATE) truoc khi kiem tra trung lich + luu don:
    // 2 nguoi dat cung phong cung luc se phai xep hang, nguoi sau kiem tra lai sau khi nguoi truoc da luu xong.
    // Cho khoa toi da 5s roi bao loi, tranh treo request
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    // BR-03: phong dang MAINTENANCE khong duoc xuat hien trong danh sach co the dat
    // BR-01: loai bo phong da co booking trung khoang ngay (chi tinh cac booking dang giu cho: PENDING/CONFIRMED/CHECKED_IN)
    @Query("""
            SELECT r FROM Room r
            WHERE r.status <> com.hotel.entity.RoomStatus.MAINTENANCE
            AND (:roomTypeId IS NULL OR r.roomType.id = :roomTypeId)
            AND (:guests IS NULL OR r.roomType.maxGuests >= :guests)
            AND r.id NOT IN (
                SELECT b.room.id FROM Booking b
                WHERE b.status IN (
                    com.hotel.entity.BookingStatus.PENDING,
                    com.hotel.entity.BookingStatus.CONFIRMED,
                    com.hotel.entity.BookingStatus.CHECKED_IN
                )
                AND b.checkInDate < :checkOutDate
                AND b.checkOutDate > :checkInDate
            )
            """)
    List<Room> findAvailableRooms(
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate,
            @Param("roomTypeId") Long roomTypeId,
            @Param("guests") Integer guests
    );
}
