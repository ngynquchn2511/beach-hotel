package com.hotel.security;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerPortalSuccessHandlerTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;

    private final CustomerPortalSuccessHandler handler = new CustomerPortalSuccessHandler();

    @Test
    void onAuthenticationSuccess_customerRole_redirectsToRoomsList() throws Exception {
        User customer = User.builder().email("customer@mail.com").role(UserRole.CUSTOMER).build();
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(customer));
        when(request.getContextPath()).thenReturn("");
        when(response.encodeRedirectURL(anyString())).thenAnswer(inv -> inv.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/customer/rooms");
    }

    @Test
    void onAuthenticationSuccess_adminRole_rejectedAndRedirectedToWrongPortal() throws Exception {
        User admin = User.builder().email("admin@hotel.com").role(UserRole.ADMIN).build();
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(admin));
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/login?error=wrong_portal");
        verify(response, never()).sendRedirect("/customer/rooms");
    }

    @Test
    void onAuthenticationSuccess_staffRole_rejectedAndRedirectedToWrongPortal() throws Exception {
        User staff = User.builder().email("staff@hotel.com").role(UserRole.STAFF).build();
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(staff));
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/login?error=wrong_portal");
    }
}
