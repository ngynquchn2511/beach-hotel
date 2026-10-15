package com.hotel.repository;

import com.hotel.entity.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {

    // Dang cat len dau, roi moi nhat
    @Query("select i from LostItem i left join fetch i.room left join fetch i.booking "
            + "order by case when i.status = com.hotel.entity.LostItem.Status.STORED then 0 else 1 end, i.foundAt desc")
    List<LostItem> findAllForList();

    long countByStatus(LostItem.Status status);
}
