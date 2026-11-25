package com.hotel.repository;

import com.hotel.entity.ChatAiSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAiSettingsRepository extends JpaRepository<ChatAiSettings, Long> {
}
