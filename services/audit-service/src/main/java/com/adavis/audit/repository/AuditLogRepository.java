package com.adavis.audit.repository;

import com.adavis.audit.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    List<AuditLog> findByEntityAndEntityIdOrderByTimestampDesc(String entity, String entityId);

    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    Page<AuditLog> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndUserIdOrderByTimestampDesc(String tenantId, String userId, Pageable pageable);

    Page<AuditLog> findByActionAndTimestampBetween(String action, Instant from, Instant to, Pageable pageable);

    @Query("{ 'action': ?0, 'status': ?1, 'timestamp': { $gte: ?2, $lt: ?3 } }")
    List<AuditLog> findByActionAndStatusAndTimestampRangeOrderByTimestampAsc(String action, String status, Instant from, Instant to);

    @Query("{ 'action': ?0, 'status': ?1, 'tenantId': ?2, 'timestamp': { $gte: ?3, $lt: ?4 } }")
    List<AuditLog> findByActionAndStatusAndTenantIdAndTimestampRangeOrderByTimestampAsc(String action, String status, String tenantId, Instant from, Instant to);

    @Query("{ 'userId': ?0, 'timestamp': { $gte: ?1, $lte: ?2 } }")
    List<AuditLog> findByUserIdAndDateRange(String userId, Instant from, Instant to);

    List<AuditLog> findByTenantIdAndTimestampBetween(String tenantId, Instant from, Instant to);

    long countByActionAndTimestampBetween(String action, Instant from, Instant to);
}