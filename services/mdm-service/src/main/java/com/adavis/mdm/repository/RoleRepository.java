package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.Role;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends MongoRepository<Role, String> {

    Optional<Role> findByRoleId(String roleId);

    List<Role> findByIsActiveTrue();

    List<Role> findByTenantIdAndIsActiveTrue(String tenantId);

    long countByIsActiveTrue();

    long countByTenantIdAndIsActiveTrue(String tenantId);

    List<Role> findByParentRoleId(String parentRoleId);

    boolean existsByRoleId(String roleId);

    boolean existsByTenantIdAndRoleCode(String tenantId, String roleCode);

    boolean existsByTenantIdAndRoleCodeAndRoleIdNot(String tenantId, String roleCode, String roleId);

    List<Role> findByTenantId(String tenantId);

    List<Role> findByTenantIdAndIsActive(String tenantId, Boolean isActive);

    List<Role> findByIsActive(Boolean isActive);
}