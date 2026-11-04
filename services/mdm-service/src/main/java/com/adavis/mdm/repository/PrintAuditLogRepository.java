package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.PrintAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PrintAuditLogRepository extends MongoRepository<PrintAuditLog, String> {

    List<PrintAuditLog> findTop50ByTenantIdOrderByTimestampDesc(String tenantId);

    Page<PrintAuditLog> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    List<PrintAuditLog> findByTenantIdAndUserIdOrderByTimestampDesc(String tenantId, String userId);

    List<PrintAuditLog> findByTenantIdAndDocumentIdOrderByTimestampDesc(String tenantId, String documentId);

    List<PrintAuditLog> findByTenantIdAndStatusOrderByTimestampDesc(String tenantId, String status);

    List<PrintAuditLog> findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
            String tenantId, Instant from, Instant to);
}

