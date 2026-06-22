package com.scanlanka.admin.app;

import com.scanlanka.admin.domain.AdminAuditLog;
import com.scanlanka.admin.infra.AdminAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records admin mutations (SEC-ADMIN-2). */
@Service
public class AuditService {

    private final AdminAuditLogRepository repo;

    public AuditService(AdminAuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void log(Long adminId, String action, String entity, String entityId, String before, String after) {
        repo.save(new AdminAuditLog(adminId, action, entity, entityId, before, after));
    }
}
