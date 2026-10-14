package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingGuestRepository;
import com.hotel.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestRegistrationServiceTest {

    @Mock private BookingGuestRepository guestRepository;
    @Mock private BookingRepository bookingRepository;

    @InjectMocks private GuestRegistrationService service;

    private Booking booking;
    private static final LocalDate DOB = LocalDate.of(1995, 5, 20);

    @BeforeEach
    void setUp() {
        booking = Booking.builder().id(7L).status(BookingStatus.CONFIRMED).build();
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(guestRepository.findByBookingIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(guestRepository.save(any(BookingGuest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private BookingGuest add(IdDocumentType type, String number) {
        return service.addGuest(7L, " Nguyễn Văn An ", DOB, Gender.MALE, type, number, "Việt Nam", null);
    }

    @Test
    void addGuest_validCccd_trimsAndSaves() {
        BookingGuest g = add(IdDocumentType.CCCD, " 001 095 012345 ");
        assertThat(g.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(g.getIdNumber()).isEqualTo("001095012345");
        assertThat(g.getBooking()).isSameAs(booking);
    }

    @ParameterizedTest
    @CsvSource({"CCCD,12345", "CCCD,00109501234A", "PASSPORT,AB12", "PASSPORT,C1234567890"})
    void addGuest_invalidIdNumber_rejected(IdDocumentType type, String number) {
        assertThatThrownBy(() -> add(type, number)).isInstanceOf(BusinessException.class);
    }

    @Test
    void addGuest_passportUppercased() {
        assertThat(add(IdDocumentType.PASSPORT, "c1234567").getIdNumber()).isEqualTo("C1234567");
    }

    @Test
    void addGuest_missingOrFutureBirthDate_rejected() {
        assertThatThrownBy(() -> service.addGuest(7L, "An", null, Gender.MALE, IdDocumentType.CCCD, "001095012345", "Việt Nam", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.addGuest(7L, "An", LocalDate.now().plusDays(1), Gender.MALE, IdDocumentType.CCCD, "001095012345", "Việt Nam", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addGuest_sameDocumentTwice_rejected() {
        BookingGuest existing = BookingGuest.builder().idType(IdDocumentType.CCCD).idNumber("001095012345").build();
        when(guestRepository.findByBookingIdOrderByIdAsc(7L)).thenReturn(List.of(existing));
        assertThatThrownBy(() -> add(IdDocumentType.CCCD, "001095012345"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã được khai báo");
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING", "CHECKED_OUT", "CANCELLED"})
    void addGuest_wrongBookingStatus_rejected(BookingStatus status) {
        booking.setStatus(status);
        assertThatThrownBy(() -> add(IdDocumentType.CCCD, "001095012345")).isInstanceOf(BusinessException.class);
        verify(guestRepository, never()).save(any());
    }

    @Test
    void removeGuest_ofOtherBooking_rejected() {
        Booking other = Booking.builder().id(8L).status(BookingStatus.CONFIRMED).build();
        when(guestRepository.findById(3L)).thenReturn(Optional.of(BookingGuest.builder().id(3L).booking(other).build()));
        assertThatThrownBy(() -> service.removeGuest(7L, 3L)).isInstanceOf(BusinessException.class);
        verify(guestRepository, never()).delete(any());
    }

    @Test
    void findStaying_invalidRange_rejected() {
        LocalDate d = LocalDate.now();
        assertThatThrownBy(() -> service.findStaying(d, d.minusDays(1))).isInstanceOf(BusinessException.class);
    }
}
