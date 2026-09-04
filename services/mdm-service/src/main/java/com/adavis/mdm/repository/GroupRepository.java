package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.Group;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends MongoRepository<Group, String> {

    Optional<Group> findByGroupId(String groupId);

    List<Group> findByIsActiveTrue();

    List<Group> findByTenantIdAndIsActiveTrue(String tenantId);

    long countByIsActiveTrue();

    long countByTenantIdAndIsActiveTrue(String tenantId);

    boolean existsByGroupId(String groupId);

    boolean existsByTenantIdAndGroupCode(String tenantId, String groupCode);

    boolean existsByTenantIdAndGroupCodeAndGroupIdNot(String tenantId, String groupCode, String groupId);

    List<Group> findByTenantId(String tenantId);

    List<Group> findByTenantIdAndIsActive(String tenantId, Boolean isActive);

    List<Group> findByIsActive(Boolean isActive);
}