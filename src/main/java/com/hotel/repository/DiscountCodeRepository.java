package com.hotel.repository;

import com.hotel.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCodeAndActiveTrue(String code);

    // Khoa dong ma khi tao don: 2 nguoi cung dung luot cuoi thi nguoi sau phai cho nguoi truoc ghi xong roi moi dem lai luot
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from DiscountCode d where d.code = :code and d.active = true")
    Optional<DiscountCode> findActiveForUpdate(@org.springframework.data.repository.query.Param("code") String code);

    boolean existsByCode(String code);

    List<DiscountCode> findByActiveTrue();
}
