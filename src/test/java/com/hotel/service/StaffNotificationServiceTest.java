package com.hotel.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

// Thong bao le tan: gui ngay khi khong co giao dich, cho commit neu dang trong giao dich, giu 50 cai gan nhat
class StaffNotificationServiceTest {

    private final StaffNotificationService service = new StaffNotificationService();

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutTransaction_publishedImmediately_bufferKeepsLast50() {
        for (int i = 1; i <= 60; i++) {
            service.notify("booking", "Don " + i, null, "/admin/bookings/" + i);
        }
        assertThat(service.recent(0)).hasSize(StaffNotificationService.BUFFER);
        assertThat(service.recent(0).get(0).title()).isEqualTo("Don 11");
        assertThat(service.recent(58)).extracting(StaffNotificationService.Notice::title).containsExactly("Don 59", "Don 60");
        assertThat(service.lastId()).isEqualTo(60);
    }

    @Test
    void insideTransaction_waitsForCommit_droppedOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        service.notify("booking", StaffNotificationService.Level.SUCCESS, "Coc VNPay", "1.000.000 VND", "/admin/bookings/1");
        assertThat(service.recent(0)).isEmpty();
        // Rollback: chi goi afterCompletion -> khong bao
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(service.recent(0)).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(service.recent(0)).singleElement().satisfies(n -> {
            assertThat(n.level()).isEqualTo(StaffNotificationService.Level.SUCCESS);
            assertThat(n.url()).isEqualTo("/admin/bookings/1");
        });
    }

    @Test
    void subscribe_registersConnection() {
        service.notify("contact", "Lien he", "x", "/admin/contact-messages");
        service.subscribe(0L);
        assertThat(service.connections()).isEqualTo(1);
        // Gui them khi da co ket noi khong loi
        service.notify("contact", "Lien he 2", "y", "/admin/contact-messages");
        service.heartbeat();
        assertThat(service.recent(0)).hasSize(2);
    }
}
