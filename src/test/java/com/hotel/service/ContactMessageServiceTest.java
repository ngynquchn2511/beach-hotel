package com.hotel.service;

import com.hotel.entity.ContactMessage;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ContactMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactMessageServiceTest {

    @Mock private ContactMessageRepository repository;
    @Mock private EmailService emailService;

    @InjectMocks
    private ContactMessageService service;

    @Test
    void submit_valid_trimsAndSavesAsNew() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContactMessage m = service.submit("  Nguyen Van A ", " a@mail.com ", "", "  Cho hoi phong gia dinh con trong khong?  ");

        assertEquals("Nguyen Van A", m.getFullName());
        assertEquals("a@mail.com", m.getEmail());
        assertNull(m.getPhone());
        assertEquals("Cho hoi phong gia dinh con trong khong?", m.getMessage());
        assertFalse(m.isHandled());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''           | a@mail.com | ''          | Noi dung du dai roi day | contact.error.name",
            "Nguyen Van A | khong-email| ''          | Noi dung du dai roi day | contact.error.email",
            "Nguyen Van A | a@mail     | ''          | Noi dung du dai roi day | contact.error.email",
            "Nguyen Van A | a@mail.com | abc123      | Noi dung du dai roi day | contact.error.phone",
            "Nguyen Van A | a@mail.com | 0912345678  | ngan                    | contact.error.message",
    })
    void submit_invalid_throwsAndDoesNotSave(String name, String email, String phone, String message, String code) {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(name, email, phone, message));
        assertEquals(BusinessException.vietnamese(code, ContactMessageService.MIN_MESSAGE_LENGTH,
                ContactMessageService.MAX_MESSAGE_LENGTH), ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void submit_messageTooLong_throws() {
        String longText = "x".repeat(ContactMessageService.MAX_MESSAGE_LENGTH + 1);
        assertThrows(BusinessException.class, () -> service.submit("A", "a@mail.com", null, longText));
    }

    @Test
    void submit_overHourlyLimit_throws() {
        when(repository.countByEmailIgnoreCaseAndCreatedAtAfter(eq("a@mail.com"), any(LocalDateTime.class)))
                .thenReturn((long) ContactMessageService.MAX_PER_HOUR);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submit("A", "a@mail.com", null, "Noi dung du dai roi day"));
        assertEquals(BusinessException.vietnamese("contact.error.tooMany"), ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void reply_savesMarksHandledAndEmails() {
        ContactMessage m = ContactMessage.builder().id(5L).fullName("A").email("a@mail.com").message("Hoi gia phong").build();
        when(repository.findById(5L)).thenReturn(Optional.of(m));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContactMessage saved = service.reply(5L, "  Con phong a  ", "Le Tan");

        assertEquals("Con phong a", saved.getReply());
        assertNotNull(saved.getRepliedAt());
        assertTrue(saved.isHandled());
        assertEquals("Le Tan", saved.getHandledBy());
        verify(emailService).sendContactReply(saved);
    }

    @Test
    void reply_blank_throwsWithoutEmail() {
        when(repository.findById(5L)).thenReturn(Optional.of(ContactMessage.builder().id(5L).build()));

        assertThrows(BusinessException.class, () -> service.reply(5L, "   ", "Le Tan"));
        verify(emailService, never()).sendContactReply(any());
    }

    @Test
    void toggleHandled_flipsAndClearsHandler() {
        ContactMessage m = ContactMessage.builder().id(7L).handled(false).build();
        when(repository.findById(7L)).thenReturn(Optional.of(m));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.toggleHandled(7L, "Le Tan").isHandled());
        assertEquals("Le Tan", m.getHandledBy());
        assertFalse(service.toggleHandled(7L, "Le Tan").isHandled());
        assertNull(m.getHandledBy());
    }

    @Test
    void findForAdmin_filtersByStatusAndQuery() {
        ContactMessage open = ContactMessage.builder().fullName("Tran B").email("b@mail.com").message("Dat tiec cuoi").handled(false).build();
        ContactMessage done = ContactMessage.builder().fullName("Le C").email("c@mail.com").phone("0988777666").message("Gia phong").handled(true).build();
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(open, done));

        assertEquals(List.of(open), service.findForAdmin("NEW", null));
        assertEquals(List.of(done), service.findForAdmin("HANDLED", ""));
        assertEquals(List.of(open, done), service.findForAdmin(null, null));
        assertEquals(List.of(open), service.findForAdmin(null, "TIEC"));
        assertEquals(List.of(done), service.findForAdmin("", "0988"));
        verify(emailService, never()).sendContactReply(any());
        verify(repository, never()).countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any());
    }
}
