package com.hotel.integration;

import com.hotel.entity.*;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Hoa don PDF: tai ve (le tan / chu don) va dinh kem email khi tra phong
@TestPropertySource(properties = {"spring.mail.username=resort@test.vn", "spring.mail.password=secret"})
class InvoicePdfIT extends ItFixtures {

    @MockBean private JavaMailSender mailSender;

    private User staff;
    private User guest;
    private Booking booking;

    @BeforeEach
    void seed() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        staff = persistUser(UserRole.STAFF, null);
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Room room = persistRoom(persistRoomType("Phòng Hướng Biển IT", 1_200_000, 2), 1_200_000, RoomStatus.OCCUPIED);
        booking = persistBooking(guest, room, BookingStatus.CHECKED_IN, -2, 2);
        declareGuest(booking);
    }

    private void checkOut() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/check-out", booking.getId()).with(csrf()).with(as(staff))
                .param("paymentMethod", "CASH"));
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void checkout_emailsInvoiceWithPdfAttachment() throws Exception {
        checkOut();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        MimeMessage invoiceMail = sent.getAllValues().stream()
                .filter(m -> {
                    try {
                        return m.getSubject().contains("Hóa đơn");
                    } catch (Exception e) {
                        return false;
                    }
                }).findFirst().orElseThrow();
        Multipart parts = (Multipart) invoiceMail.getContent();
        Part attachment = null;
        for (int i = 0; i < parts.getCount(); i++) {
            if (Part.ATTACHMENT.equalsIgnoreCase(parts.getBodyPart(i).getDisposition())) {
                attachment = parts.getBodyPart(i);
            }
        }
        assertThat(attachment).isNotNull();
        assertThat(attachment.getFileName()).startsWith("HoaDon-HD").endsWith("-" + booking.getId() + ".pdf");
        // Header Content-Type chi duoc dien luc gui that -> doc kieu tu DataHandler
        assertThat(attachment.getDataHandler().getContentType()).startsWith("application/pdf");
        byte[] pdf = attachment.getInputStream().readAllBytes();
        assertThat(pdfText(pdf)).contains("HÓA ĐƠN THANH TOÁN").contains("2.400.000");
    }

    @Test
    void staffAndOwnerCanDownloadPdf_othersCannot() throws Exception {
        // Chua thanh toan -> chua co hoa don
        mockMvc.perform(get("/admin/bookings/{id}/invoice.pdf", booking.getId()).with(as(staff)))
                .andExpect(redirectedUrl("/admin/bookings/" + booking.getId()))
                .andExpect(flash().attribute("errorMessage", containsString("chưa được thanh toán")));

        checkOut();

        byte[] pdf = mockMvc.perform(get("/admin/bookings/{id}/invoice.pdf", booking.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("HoaDon-HD")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = pdfText(pdf);
        assertThat(text).contains("Sóng Xanh Resort").contains("Phòng Hướng Biển IT").contains(guest.getFullName());

        mockMvc.perform(get("/customer/bookings/{id}/invoice.pdf", booking.getId()).with(as(guest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));

        User other = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        mockMvc.perform(get("/customer/bookings/{id}/invoice.pdf", booking.getId()).with(as(other)))
                .andExpect(redirectedUrl("/customer/bookings"));
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }
}
