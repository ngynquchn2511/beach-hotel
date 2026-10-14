package com.hotel.service;

import com.hotel.entity.AuditAction;
import com.hotel.entity.AuditLog;
import com.hotel.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final Object PENDING_KEY = new Object();

    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate newTransaction;

    public AuditLogService(AuditLogRepository auditLogRepository, PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Duoc AuditEntityListener goi ngay trong luc Hibernate flush. Chi luu log SAU KHI giao dich chinh commit thanh cong:
    // neu thao tac bi rollback (VD: loi nghiep vu) thi khong de lai log "ma" cho mot thay doi khong he xay ra.
    @SuppressWarnings("unchecked")
    public void record(AuditLog entry) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            saveAll(List.of(entry));
            return;
        }
        List<AuditLog> pending = (List<AuditLog>) TransactionSynchronizationManager.getResource(PENDING_KEY);
        if (pending == null) {
            List<AuditLog> list = new ArrayList<>();
            TransactionSynchronizationManager.bindResource(PENDING_KEY, list);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_KEY);
                    if (status == STATUS_COMMITTED) {
                        saveAll(list);
                    }
                }
            });
            pending = list;
        }
        pending.add(entry);
    }

    private void saveAll(List<AuditLog> entries) {
        if (entries.isEmpty()) {
            return;
        }
        try {
            newTransaction.executeWithoutResult(status -> auditLogRepository.saveAll(entries));
        } catch (Exception ex) {
            // Loi ghi log khong duoc lam hong thao tac chinh cua nguoi dung
            log.error("Ghi nhat ky thao tac that bai", ex);
        }
    }

    public Page<AuditLog> search(String entityType, AuditAction action, String actorRole,
                                 LocalDate fromDate, LocalDate toDate, String keyword, int page, int size) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return auditLogRepository.search(
                blankToNull(entityType), action, blankToNull(actorRole),
                fromDate != null ? fromDate.atStartOfDay() : null,
                toDate != null ? toDate.plusDays(1).atStartOfDay() : null,
                kw != null ? "%" + kw.toLowerCase() + "%" : null,
                kw,
                PageRequest.of(Math.max(0, page - 1), size));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
