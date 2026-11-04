package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.PrintDelegation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrintDelegationRepository extends MongoRepository<PrintDelegation, String> {

    List<PrintDelegation> findByTenantIdAndIsActiveTrue(String tenantId);

    List<PrintDelegation> findByTenantIdAndDelegateeIdAndIsActiveTrue(String tenantId, String delegateeId);

    List<PrintDelegation> findByTenantIdAndDelegateeIdInAndIsActiveTrue(String tenantId, List<String> delegateeIds);

    Optional<PrintDelegation> findByTenantIdAndDelegateeTypeAndDelegateeIdAndIsActiveTrue(
            String tenantId, String delegateeType, String delegateeId);
}

