package com.hotel.repository;

import com.hotel.entity.UpgradeOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UpgradeOfferRepository extends JpaRepository<UpgradeOffer, Long> {

    @Query("select o from UpgradeOffer o join fetch o.room r join fetch r.roomType where o.booking.id = :bookingId order by o.createdAt desc, o.id")
    List<UpgradeOffer> findByBooking(@Param("bookingId") Long bookingId);

    boolean existsByBookingId(Long bookingId);

    @Query("select o from UpgradeOffer o join fetch o.booking b join fetch b.room join fetch o.room r join fetch r.roomType "
            + "order by o.createdAt desc")
    List<UpgradeOffer> findAllForList();
}
