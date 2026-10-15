package com.hotel.service;

import com.hotel.entity.ContactMessage;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ContactMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

// Form "Lien he" o trang gioi thieu: khach gui yeu cau tu van -> le tan xem trong hop thu, tra loi qua email
@Service
public class ContactMessageService {

    public static final int MAX_MESSAGE_LENGTH = 2000;
    public static final int MIN_MESSAGE_LENGTH = 10;
    // Chong spam: 1 email gui toi da 3 tin moi gio
    public static final int MAX_PER_HOUR = 3;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 .]{9,15}$");

    private final ContactMessageRepository repository;
    private final EmailService emailService;

    public ContactMessageService(ContactMessageRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    @Transactional
    public ContactMessage submit(String fullName, String email, String phone, String message) {
        String name = trim(fullName);
        String mail = trim(email);
        String tel = trim(phone);
        String body = trim(message);
        if (name.isEmpty() || name.length() > 100) {
            throw BusinessException.of("contact.error.name");
        }
        if (mail.length() > 150 || !EMAIL.matcher(mail).matches()) {
            throw BusinessException.of("contact.error.email");
        }
        if (!tel.isEmpty() && !PHONE.matcher(tel).matches()) {
            throw BusinessException.of("contact.error.phone");
        }
        if (body.length() < MIN_MESSAGE_LENGTH || body.length() > MAX_MESSAGE_LENGTH) {
            throw BusinessException.of("contact.error.message", MIN_MESSAGE_LENGTH, MAX_MESSAGE_LENGTH);
        }
        if (repository.countByEmailIgnoreCaseAndCreatedAtAfter(mail, LocalDateTime.now().minusHours(1)) >= MAX_PER_HOUR) {
            throw BusinessException.of("contact.error.tooMany");
        }
        return repository.save(ContactMessage.builder()
                .fullName(name).email(mail).phone(tel.isEmpty() ? null : tel).message(body)
                .handled(false).build());
    }

    // status: "NEW" = chua xu ly, "HANDLED" = da xu ly, con lai = tat ca. q: tim theo ten / email / SDT / noi dung
    @Transactional(readOnly = true)
    public List<ContactMessage> findForAdmin(String status, String q) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> !"NEW".equals(status) || !m.isHandled())
                .filter(m -> !"HANDLED".equals(status) || m.isHandled())
                .filter(m -> needle.isEmpty() || contains(m.getFullName(), needle) || contains(m.getEmail(), needle)
                        || contains(m.getPhone(), needle) || contains(m.getMessage(), needle))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countNew() {
        return repository.countByHandledFalse();
    }

    // Tra loi khach qua email va danh dau da xu ly
    @Transactional
    public ContactMessage reply(Long id, String text, String staffName) {
        ContactMessage m = get(id);
        String body = trim(text);
        if (body.isEmpty()) {
            throw new BusinessException("Nội dung trả lời không được để trống");
        }
        if (body.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("Câu trả lời tối đa " + MAX_MESSAGE_LENGTH + " ký tự");
        }
        m.setReply(body);
        m.setRepliedAt(LocalDateTime.now());
        m.setHandled(true);
        m.setHandledBy(staffName);
        ContactMessage saved = repository.save(m);
        emailService.sendContactReply(saved);
        return saved;
    }

    // Danh dau da xu ly (VD da goi dien cho khach) / mo lai
    @Transactional
    public ContactMessage toggleHandled(Long id, String staffName) {
        ContactMessage m = get(id);
        m.setHandled(!m.isHandled());
        m.setHandledBy(m.isHandled() ? staffName : null);
        return repository.save(m);
    }

    private ContactMessage get(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy tin liên hệ"));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }
}
