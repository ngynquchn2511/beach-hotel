package com.hotel.service;

import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerManagementService customerManagementService;

    @Test
    void findAllCustomers_onlyRequestsCustomerRole() {
        when(userRepository.findByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER)).thenReturn(List.of());
        customerManagementService.findAllCustomers();
        verify(userRepository).findByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER);
    }

    @Test
    void updateCustomerType_nonCustomerRole_throws() {
        User staff = User.builder().id(1L).role(UserRole.STAFF).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(staff));

        assertThrows(BusinessException.class, () -> customerManagementService.updateCustomerType(1L, CustomerType.VIP));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateCustomerType_customerRole_success() {
        User customer = User.builder().id(1L).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        customerManagementService.updateCustomerType(1L, CustomerType.VIP);

        assertEquals(CustomerType.VIP, customer.getCustomerType());
        verify(userRepository).save(customer);
    }

    @Test
    void findOrCreateWalkInCustomer_existingPhone_returnsExisting() {
        User existing = User.builder().id(5L).phoneNumber("0909999999").build();
        when(userRepository.findByPhoneNumber("0909999999")).thenReturn(Optional.of(existing));

        User result = customerManagementService.findOrCreateWalkInCustomer("Khach", "0909999999");

        assertEquals(5L, result.getId());
        verify(userRepository, never()).save(any());
    }

    @Test
    void findOrCreateWalkInCustomer_newPhone_createsWithNewCustomerType() {
        when(userRepository.findByPhoneNumber("0908888888")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = customerManagementService.findOrCreateWalkInCustomer("Khach Moi", "0908888888");

        assertEquals(CustomerType.NEW, result.getCustomerType());
        assertEquals(UserRole.CUSTOMER, result.getRole());
        assertTrue(result.getEmail().contains("0908888888"));
    }

    @Test
    void findOrCreateGuestCustomer_existingEmail_returnsExisting() {
        User existing = User.builder().id(6L).email("guest@mail.com").build();
        when(userRepository.findByEmail("guest@mail.com")).thenReturn(Optional.of(existing));

        User result = customerManagementService.findOrCreateGuestCustomer("Khach", "guest@mail.com", "0901111111");

        assertEquals(6L, result.getId());
        verify(userRepository, never()).save(any());
    }
}
