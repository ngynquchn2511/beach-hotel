package com.hotel.repository;

import com.hotel.entity.SpaPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpaPackageRepository extends JpaRepository<SpaPackage, Long> {

    List<SpaPackage> findByActiveTrueOrderByPriceAscNameAsc();

    List<SpaPackage> findAllByOrderByActiveDescPriceAscNameAsc();
}
