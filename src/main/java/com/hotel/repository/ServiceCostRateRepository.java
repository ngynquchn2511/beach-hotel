package com.hotel.repository;

import com.hotel.entity.ChargeType;
import com.hotel.entity.ServiceCostRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCostRateRepository extends JpaRepository<ServiceCostRate, ChargeType> {
}
