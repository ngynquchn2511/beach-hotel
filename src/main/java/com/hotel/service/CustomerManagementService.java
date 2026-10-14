package com.hotel.service;

import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAllCustomers() {
        return userRepository.findByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy khách hàng"));
    }

    @Transactional
    public void updateCustomerType(Long id, CustomerType type) {
        User user = findById(id);
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BusinessException("Chỉ có thể cập nhật loại khách hàng cho tài khoản CUSTOMER");
        }
        user.setCustomerType(type);
        userRepository.save(user);
    }

    @Transactional
    public void toggleLocked(Long id) {
        User user = findById(id);
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BusinessException("Chỉ có thể khóa/mở khóa tài khoản CUSTOMER tại đây");
        }
        user.setLocked(!user.isLocked());
        userRepository.save(user);
    }

    // Dung khi nhan vien dat phong tai quay cho khach vang lai chua co tai khoan.
    // Tim theo SDT truoc, neu chua co thi tao tai khoan moi voi mat khau ngau nhien (khach co the dang ky lai sau de dat mat khau rieng)
    @Transactional
    public User findOrCreateWalkInCustomer(String fullName, String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> {
                    User user = User.builder()
                            .fullName(fullName)
                            .phoneNumber(phoneNumber)
                            .email(phoneNumber + "@khachvanglai.local")
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role(UserRole.CUSTOMER)
                            .customerType(CustomerType.NEW)
                            .build();
                    return userRepository.save(user);
                });
    }

    // Dung khi khach dat phong online nhung khong dang nhap (khach vang lai qua web).
    // Tim theo email truoc (vi email la duy nhat), neu chua co thi tu tao tai khoan
    // voi mat khau ngau nhien - khach van dat phong duoc ma khong can biet minh "co tai khoan"
    @Transactional
    public User findOrCreateGuestCustomer(String fullName, String email, String phoneNumber) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User user = User.builder()
                            .fullName(fullName)
                            .email(email)
                            .phoneNumber(phoneNumber)
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role(UserRole.CUSTOMER)
                            .customerType(CustomerType.NEW)
                            .build();
                    return userRepository.save(user);
                });
    }
}
