package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.PrintPermission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrintPermissionRepository extends MongoRepository<PrintPermission, String> {

    List<PrintPermission> findByTenantIdAndTargetTypeAndTargetIdAndIsActiveTrue(
            String tenantId, String targetType, String targetId);

    List<PrintPermission> findByTenantIdAndTargetTypeAndIsActiveTrue(String tenantId, String targetType);

    List<PrintPermission> findByTenantIdAndIsActiveTrue(String tenantId);

    Optional<PrintPermission> findByTenantIdAndTargetTypeAndTargetIdAndModuleAndReportTypeAndIsActiveTrue(
            String tenantId, String targetType, String targetId, String module, String reportType);

    List<PrintPermission> findByTenantIdAndTargetTypeAndTargetIdInAndIsActiveTrue(
            String tenantId, String targetType, List<String> targetIds);
}

