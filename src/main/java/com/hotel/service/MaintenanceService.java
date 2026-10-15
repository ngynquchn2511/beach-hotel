package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.MaintenanceTicket;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.MaintenanceTicketRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

// Bao hong / bao tri phong: phieu co the khoa phong (trang thai Bao tri). Sua xong -> phong sang Cho don (buong phong don lai).
// Canh bao khach sap den (trong WARN_DAYS ngay) ma phong dang bao tri -> le tan doi phong cho khach
@Service
public class MaintenanceService {

    public static final int WARN_DAYS = 3;

    private final MaintenanceTicketRepository ticketRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public MaintenanceService(MaintenanceTicketRepository ticketRepository, RoomRepository roomRepository,
                              BookingRepository bookingRepository) {
        this.ticketRepository = ticketRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<MaintenanceTicket> open() {
        return ticketRepository.findOpen();
    }

    public List<MaintenanceTicket> resolvedRecently() {
        return ticketRepository.findResolvedSince(LocalDate.now().minusDays(7).atStartOfDay());
    }

    public long countOpen() {
        return ticketRepository.countByStatusNot(MaintenanceTicket.Status.DONE);
    }

    // Don sap nhan phong (chua nhan) ma phong dang bao tri -> can doi phong
    @Transactional(readOnly = true)
    public List<Booking> arrivalsInBlockedRooms(LocalDate today) {
        LocalDate until = today.plusDays(WARN_DAYS);
        return bookingRepository.findForCalendar(today, until.plusDays(1)).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED)
                .filter(b -> !b.getCheckInDate().isAfter(until))
                .filter(b -> b.getRoom().getStatus() == RoomStatus.MAINTENANCE)
                .sorted(Comparator.comparing(Booking::getCheckInDate))
                .toList();
    }

    @Transactional
    public MaintenanceTicket report(Long roomId, String title, String description, MaintenanceTicket.Priority priority,
                                    boolean blocksRoom, LocalDate expectedDoneDate, String reportedBy) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));
        String name = title == null ? "" : title.trim();
        if (name.isEmpty() || name.length() > 150) {
            throw new BusinessException("Vui lòng nhập nội dung hỏng (tối đa 150 ký tự)");
        }
        if (expectedDoneDate != null && expectedDoneDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Ngày dự kiến xong không được ở quá khứ");
        }
        if (blocksRoom && room.getStatus() == RoomStatus.OCCUPIED) {
            throw new BusinessException("Phòng " + room.getRoomNumber()
                    + " đang có khách ở - hãy đổi phòng cho khách trước rồi mới khóa phòng để sửa (hoặc bỏ chọn khóa phòng)");
        }
        String desc = description == null || description.isBlank() ? null : description.trim();
        MaintenanceTicket ticket = ticketRepository.save(MaintenanceTicket.builder().room(room).title(name)
                .description(desc != null && desc.length() > 500 ? desc.substring(0, 500) : desc)
                .priority(priority != null ? priority : MaintenanceTicket.Priority.NORMAL)
                .status(MaintenanceTicket.Status.OPEN).blocksRoom(blocksRoom)
                .expectedDoneDate(expectedDoneDate).reportedBy(reportedBy).build());
        if (blocksRoom) {
            room.setStatus(RoomStatus.MAINTENANCE);
            roomRepository.save(room);
        }
        return ticket;
    }

    @Transactional
    public MaintenanceTicket start(Long ticketId, String staffName) {
        MaintenanceTicket ticket = requireOpen(ticketId);
        ticket.setStatus(MaintenanceTicket.Status.IN_PROGRESS);
        ticket.setAssignee(staffName);
        return ticketRepository.save(ticket);
    }

    // Sua xong: het phieu khoa phong nao -> phong sang Cho don (bang Don phong) truoc khi don khach
    @Transactional
    public MaintenanceTicket resolve(Long ticketId, String note, String staffName) {
        MaintenanceTicket ticket = requireOpen(ticketId);
        ticket.setStatus(MaintenanceTicket.Status.DONE);
        ticket.setResolvedAt(LocalDateTime.now());
        if (ticket.getAssignee() == null) {
            ticket.setAssignee(staffName);
        }
        String text = note == null ? null : note.trim();
        ticket.setResolutionNote(text == null || text.isEmpty() ? null : text.substring(0, Math.min(300, text.length())));
        ticketRepository.save(ticket);

        Room room = ticket.getRoom();
        boolean stillBlocked = ticketRepository.findByRoomIdAndStatusNot(room.getId(), MaintenanceTicket.Status.DONE).stream()
                .anyMatch(MaintenanceTicket::isBlocking);
        if (Boolean.TRUE.equals(ticket.getBlocksRoom()) && !stillBlocked && room.getStatus() == RoomStatus.MAINTENANCE) {
            room.setStatus(RoomStatus.NOT_READY);
            room.setDirtySince(LocalDateTime.now());
            room.setCleaningBy(null);
            room.setCleaningStartedAt(null);
            roomRepository.save(room);
        }
        return ticket;
    }

    private MaintenanceTicket requireOpen(Long ticketId) {
        MaintenanceTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phiếu báo hỏng"));
        if (!ticket.isOpen()) {
            throw new BusinessException("Phiếu #" + ticketId + " đã xong");
        }
        return ticket;
    }
}
