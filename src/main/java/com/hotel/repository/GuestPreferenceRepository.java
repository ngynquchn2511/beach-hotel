package com.hotel.repository;

import com.hotel.entity.GuestPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestPreferenceRepository extends JpaRepository<GuestPreference, Long> {

    Optional<GuestPreference> findByUserId(Long userId);
}
