package com.hotel.audit;

import com.hotel.entity.AuditAction;
import com.hotel.entity.AuditLog;
import com.hotel.security.CustomUserDetails;
import jakarta.persistence.Entity;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.Type;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

// Lang nghe moi lan Hibernate INSERT / UPDATE / DELETE bat ky entity nao -> tao 1 dong AuditLog
// ghi lai ai lam, luc nao, va gia tri cu -> moi cua tung truong. Vi nam o tang Hibernate nen khong
// bo sot thao tac nao, du no di tu controller, service hay scheduler.
public class AuditEntityListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    // Khong bao gio ghi gia tri that cua cac truong nay vao log
    // So giay to tuy than cua khach luu tru la du lieu ca nhan nhay cam - cung khong ghi vao log
    private static final Set<String> MASKED_FIELDS = Set.of("password", "resetToken", "idNumber", "accessToken");
    // Truong ky thuat, khong co y nghia nghiep vu (VD: co "don moi" tu tat khi nhan vien mo xem don)
    private static final Set<String> IGNORED_FIELDS = Set.of("newBooking");

    private final Consumer<AuditLog> sink;

    public AuditEntityListener(Consumer<AuditLog> sink) {
        this.sink = sink;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (event.getEntity() instanceof AuditLog) {
            return;
        }
        List<String> changes = describeState(event.getPersister(), event.getState());
        sink.accept(build(AuditAction.CREATE, event.getEntity(), event.getId(), changes));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (event.getEntity() instanceof AuditLog) {
            return;
        }
        EntityPersister persister = event.getPersister();
        String[] names = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();
        Object[] oldState = event.getOldState();
        Object[] newState = event.getState();

        List<String> changes = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (types[i].isCollectionType() || IGNORED_FIELDS.contains(names[i])) {
                continue;
            }
            Object oldValue = oldState != null ? oldState[i] : null;
            Object newValue = newState[i];
            if (oldState != null && sameValue(oldValue, newValue)) {
                continue;
            }
            String label = AuditLabels.fieldLabel(names[i]);
            if (MASKED_FIELDS.contains(names[i])) {
                changes.add(label + ": (đã thay đổi)");
            } else if (oldState == null) {
                changes.add(label + ": " + format(newValue));
            } else {
                changes.add(label + ": " + format(oldValue) + " → " + format(newValue));
            }
        }
        // Chi doi cac truong bi bo qua (VD: danh dau da xem don) -> khong ghi log
        if (changes.isEmpty()) {
            return;
        }
        sink.accept(build(AuditAction.UPDATE, event.getEntity(), event.getId(), changes));
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (event.getEntity() instanceof AuditLog) {
            return;
        }
        List<String> changes = describeState(event.getPersister(), event.getDeletedState());
        sink.accept(build(AuditAction.DELETE, event.getEntity(), event.getId(), changes));
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private List<String> describeState(EntityPersister persister, Object[] state) {
        List<String> lines = new ArrayList<>();
        if (state == null) {
            return lines;
        }
        String[] names = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();
        for (int i = 0; i < names.length; i++) {
            if (types[i].isCollectionType() || IGNORED_FIELDS.contains(names[i]) || state[i] == null) {
                continue;
            }
            String value = MASKED_FIELDS.contains(names[i]) ? "******" : format(state[i]);
            lines.add(AuditLabels.fieldLabel(names[i]) + ": " + value);
        }
        return lines;
    }

    private AuditLog build(AuditAction action, Object entity, Object id, List<String> changes) {
        AuditLog.AuditLogBuilder log = AuditLog.builder()
                .createdAt(LocalDateTime.now())
                .action(action)
                .entityType(entity.getClass().getSimpleName())
                .entityId(id != null ? id.toString() : null)
                .changes(String.join("\n", changes));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpServletRequest request = currentRequest();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            log.actorId(details.getUser().getId())
                    .actorEmail(details.getUser().getEmail())
                    .actorName(details.getUser().getFullName())
                    .actorRole(details.getUser().getRole().name());
        } else if (request != null) {
            log.actorRole("GUEST").actorName("Khách vãng lai");
        } else {
            log.actorRole("SYSTEM").actorName("Hệ thống (tác vụ tự động)");
        }

        if (request != null) {
            String forwarded = request.getHeader("X-Forwarded-For");
            String ip = (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
            log.ipAddress(truncate(ip, 64))
                    .requestUrl(truncate(request.getMethod() + " " + request.getRequestURI(), 255));
        }
        return log.build();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        if (isEntity(a) || isEntity(b)) {
            return Objects.equals(entityId(a), entityId(b));
        }
        return Objects.equals(a, b);
    }

    private static String format(Object value) {
        if (value == null) {
            return "(trống)";
        }
        if (isEntity(value)) {
            return "#" + entityId(value);
        }
        if (value instanceof BigDecimal d) {
            return d.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean b) {
            return b ? "Có" : "Không";
        }
        if (value instanceof Enum<?> e) {
            try {
                return String.valueOf(e.getClass().getMethod("getVietnameseLabel").invoke(e));
            } catch (ReflectiveOperationException ex) {
                return e.name();
            }
        }
        String text = value.toString();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private static boolean isEntity(Object value) {
        return value instanceof HibernateProxy
                || (value != null && value.getClass().isAnnotationPresent(Entity.class));
    }

    private static Object entityId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer().getIdentifier();
        }
        try {
            return value.getClass().getMethod("getId").invoke(value);
        } catch (ReflectiveOperationException ex) {
            return "?";
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
