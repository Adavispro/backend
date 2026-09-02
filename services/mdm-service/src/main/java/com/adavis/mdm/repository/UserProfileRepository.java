package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfile, String> {

    Optional<UserProfile> findByUserId(String userId);

    Optional<UserProfile> findByUsername(String username);

    Optional<UserProfile> findByEmail(String email);

    Page<UserProfile> findByIsActiveTrue(Pageable pageable);

    List<UserProfile> findByIsActiveTrue();

    List<UserProfile> findByTenantIdAndIsActiveTrue(String tenantId);

    Page<UserProfile> findByTenantIdAndIsActiveTrue(String tenantId, Pageable pageable);

    Page<UserProfile> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    Page<UserProfile> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByIsActiveTrueAndIsBlockedFalse();

    boolean existsByUserId(String userId);

    boolean existsByUserTrackId(String userTrackId);

    long countByTenantIdAndIsActiveTrueAndIsBlockedFalse(String tenantId);
}