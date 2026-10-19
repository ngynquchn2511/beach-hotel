package com.hotel.repository;

import com.hotel.entity.OtaFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OtaFeedRepository extends JpaRepository<OtaFeed, Long> {

    @Query("select f from OtaFeed f join fetch f.room r order by r.roomNumber, f.channel")
    List<OtaFeed> findAllWithRoom();

    List<OtaFeed> findByActiveTrue();

    boolean existsByRoomIdAndChannel(Long roomId, OtaFeed.Channel channel);
}
