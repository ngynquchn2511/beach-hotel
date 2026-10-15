package com.hotel.repository;

import com.hotel.entity.FavoriteRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRoomRepository extends JpaRepository<FavoriteRoom, Long> {

    Optional<FavoriteRoom> findByUserIdAndRoomId(Long userId, Long roomId);

    // Moi nhat len dau, nap san phong + loai phong de trang danh sach khong bi N+1
    @Query("select f from FavoriteRoom f join fetch f.room r join fetch r.roomType where f.user.id = :userId order by f.createdAt desc, f.id desc")
    List<FavoriteRoom> findForUser(@Param("userId") Long userId);

    @Query("select f.room.id from FavoriteRoom f where f.user.id = :userId")
    List<Long> findRoomIdsByUserId(@Param("userId") Long userId);
}
