package com.hotel.repository;

import com.hotel.entity.PricingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, Long> {
}
