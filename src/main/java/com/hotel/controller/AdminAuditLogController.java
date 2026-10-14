package com.hotel.controller;

import com.hotel.audit.AuditLabels;
import com.hotel.entity.AuditAction;
import com.hotel.entity.AuditLog;
import com.hotel.service.AuditLogService;
import com.hotel.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;

// Nhat ky thao tac - chi xem, khong sua/xoa. SecurityConfig chi cho ADMIN vao /admin/audit-logs/**
@Controller
@RequestMapping("/admin/audit-logs")
public class AdminAuditLogController {

    private static final int PAGE_SIZE = 10;
    private static final Map<String, String> ACTOR_ROLES = Map.of(
            "ADMIN", "Quản trị viên",
            "STAFF", "Nhân viên",
            "CUSTOMER", "Khách hàng",
            "GUEST", "Khách vãng lai",
            "SYSTEM", "Hệ thống"
    );

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String entityType,
                       @RequestParam(required = false) AuditAction action,
                       @RequestParam(required = false) String actorRole,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        Page<AuditLog> result = auditLogService.search(entityType, action, actorRole, fromDate, toDate, q, page, PAGE_SIZE);
        int totalPages = Math.max(1, result.getTotalPages());

        model.addAttribute("logs", result.getContent());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));

        model.addAttribute("entityTypes", AuditLabels.entityLabels());
        model.addAttribute("actions", AuditAction.values());
        model.addAttribute("actorRoles", ACTOR_ROLES);

        model.addAttribute("entityType", entityType);
        model.addAttribute("action", action);
        model.addAttribute("actorRole", actorRole);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("q", q);
        return "admin/audit-logs/list";
    }
}
