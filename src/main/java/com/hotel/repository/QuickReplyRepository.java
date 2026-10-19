package com.hotel.repository;

import com.hotel.entity.QuickReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuickReplyRepository extends JpaRepository<QuickReply, Long> {

    List<QuickReply> findAllByOrderBySortOrderAscIdAsc();

    List<QuickReply> findByActiveTrueOrderBySortOrderAscIdAsc();
}
