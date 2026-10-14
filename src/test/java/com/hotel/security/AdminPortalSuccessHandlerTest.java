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
class AdminPortalSuccessHandlerTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;

    private final AdminPortalSuccessHandler handler = new AdminPortalSuccessHandler();

    @Test
    void onAuthenticationSuccess_adminRole_redirectsToDashboard() throws Exception {
        User admin = User.builder().email("admin@hotel.com").role(UserRole.ADMIN).build();
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(admin));
        when(request.getContextPath()).thenReturn("");
        when(response.encodeRedirectURL(anyString())).thenAnswer(inv -> inv.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/admin/dashboard");
    }

    @Test
    void onAuthenticationSuccess_staffRole_redirectsToDashboard() throws Exception {
        User staff = User.builder().email("staff@hotel.com").role(UserRole.STAFF).build();
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(staff));
        when(request.getContextPath()).thenReturn("");
        when(response.encodeRedirectURL(anyString())).thenAnswer(inv -> inv.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/admin/dashboard");
    }

    @Test
    void onAuthenticationSuccess_customerRole_rejectedAndRedirectedToWrongPortal() throws Exception {
        User customer = User.builder().email("customer@mail.com").role(UserRole.CUSTOMER).build();
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(customer));
        when(request.getSession(false)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect("/admin?error=wrong_portal");
        verify(response, never()).sendRedirect("/admin/dashboard");
    }
}
