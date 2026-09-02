package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.UserGroupAssignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupAssignmentRepository extends MongoRepository<UserGroupAssignment, String> {

    Optional<UserGroupAssignment> findByUserIdAndGroupId(String userId, String groupId);

    List<UserGroupAssignment> findByUserId(String userId);

    List<UserGroupAssignment> findByGroupId(String groupId);

    List<UserGroupAssignment> findByUserIdAndIsActiveTrue(String userId);

    List<UserGroupAssignment> findByGroupIdAndIsActiveTrue(String groupId);

    List<UserGroupAssignment> findByGroupIdInAndIsActiveTrue(List<String> groupIds);

    List<UserGroupAssignment> findByUserIdAndIsActive(String userId, Boolean isActive);

    List<UserGroupAssignment> findByGroupIdAndIsActive(String groupId, Boolean isActive);

    boolean existsByUserIdAndGroupIdAndIsActiveTrue(String userId, String groupId);
}