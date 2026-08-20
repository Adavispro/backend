package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.WorkflowTransition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowTransitionRepository extends MongoRepository<WorkflowTransition, String> {

    Optional<WorkflowTransition> findByTransitionId(String transitionId);

    List<WorkflowTransition> findByWorkflowCode(String workflowCode);

    List<WorkflowTransition> findByWorkflowCodeAndWorkflowVersion(String workflowCode, String workflowVersion);

    List<WorkflowTransition> findByWorkflowCodeAndFromStageCode(String workflowCode, String fromStageCode);

    Optional<WorkflowTransition> findByWorkflowCodeAndFromStageCodeAndActionCode(
            String workflowCode, String fromStageCode, String actionCode);

    Optional<WorkflowTransition> findByWorkflowCodeAndWorkflowVersionAndFromStageCodeAndActionCode(
            String workflowCode, String workflowVersion, String fromStageCode, String actionCode);
}
