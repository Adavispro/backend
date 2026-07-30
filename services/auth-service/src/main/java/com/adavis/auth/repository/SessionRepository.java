package com.adavis.auth.repository;

import com.adavis.auth.model.entity.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends MongoRepository<Session, String> {

    Optional<Session> findByRefreshToken(String refreshToken);

    List<Session> findByUserIdAndIsActiveTrue(String userId);

    List<Session> findByTenantIdAndIsActiveTrue(String tenantId);

    List<Session> findByIsActiveTrue();

    List<Session> findByUserIdAndIpAddressAndIsActiveTrue(String userId, String ipAddress);

    List<Session> findByIsActiveTrueAndExpiresAtBefore(Instant expiresAt);

    List<Session> findByTenantIdAndIsActiveTrueAndExpiresAtBefore(String tenantId, Instant expiresAt);
}