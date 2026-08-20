package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.WorkflowAction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowActionRepository extends MongoRepository<WorkflowAction, String> {

    Optional<WorkflowAction> findByActionId(String actionId);

    Optional<WorkflowAction> findByActionCode(String actionCode);

    List<WorkflowAction> findByActionCodeIn(List<String> actionCodes);

    List<WorkflowAction> findByApplicableRole(String applicableRole);
}
