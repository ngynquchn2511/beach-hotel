package com.hotel.repository;

import com.hotel.entity.OtaBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OtaBlockRepository extends JpaRepository<OtaBlock, Long> {

    // Dot dang khoa phong (chua ghi nhan thanh don, kenh chua xoa) giao voi [from, to)
    @Query("select o from OtaBlock o join fetch o.feed where o.room.id = :roomId and o.booking is null and o.removedAt is null "
            + "and o.startDate < :to and o.endDate > :from order by o.startDate")
    List<OtaBlock> findBlocking(@Param("roomId") Long roomId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select o from OtaBlock o where o.room.id = :roomId and o.booking is null and o.removedAt is null and o.endDate > :from")
    List<OtaBlock> findBlockingFrom(@Param("roomId") Long roomId, @Param("from") LocalDate from);

    List<OtaBlock> findByFeedId(Long feedId);

    // Danh sach cho le tan: dot sap toi / dang o, kem phong + kenh
    @Query("select o from OtaBlock o join fetch o.feed f join fetch o.room r left join fetch o.booking "
            + "where o.endDate >= :from order by o.startDate, r.roomNumber")
    List<OtaBlock> findUpcoming(@Param("from") LocalDate from);

    // Xuat lich: dot dang khoa trong khoang ngay (moi phong)
    @Query("select o from OtaBlock o join fetch o.feed join fetch o.room where o.booking is null and o.removedAt is null "
            + "and o.startDate < :to and o.endDate > :from")
    List<OtaBlock> findBlockingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // Don cua he thong da ghi nhan tu dot OTA (de biet don nao den tu kenh nao khi xuat lich)
    @Query("select o from OtaBlock o join fetch o.feed where o.booking is not null")
    List<OtaBlock> findLinked();

    long countByRemovedAtIsNotNullAndBookingIsNotNull();
}
