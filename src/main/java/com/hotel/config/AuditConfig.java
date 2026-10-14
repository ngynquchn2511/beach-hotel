package com.hotel.config;

import com.hotel.audit.AuditEntityListener;
import com.hotel.service.AuditLogService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.context.annotation.Configuration;

// Gan AuditEntityListener vao Hibernate de moi INSERT / UPDATE / DELETE deu duoc ghi nhat ky
@Configuration
public class AuditConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final AuditLogService auditLogService;

    public AuditConfig(EntityManagerFactory entityManagerFactory, AuditLogService auditLogService) {
        this.entityManagerFactory = entityManagerFactory;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    public void registerListeners() {
        AuditEntityListener listener = new AuditEntityListener(auditLogService::record);
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, listener);
        registry.appendListeners(EventType.POST_UPDATE, listener);
        registry.appendListeners(EventType.POST_DELETE, listener);
    }
}
