package com.hotel.repository;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleOrderByCreatedAtDesc(UserRole role);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByResetToken(String resetToken);
}
