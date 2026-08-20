package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.WorkflowStage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowStageRepository extends MongoRepository<WorkflowStage, String> {

    Optional<WorkflowStage> findByStageId(String stageId);

    List<WorkflowStage> findByWorkflowCode(String workflowCode);

    List<WorkflowStage> findByWorkflowCodeAndWorkflowVersion(String workflowCode, String workflowVersion);

    Optional<WorkflowStage> findByWorkflowCodeAndStageCode(String workflowCode, String stageCode);

    Optional<WorkflowStage> findByWorkflowCodeAndWorkflowVersionAndStageCode(
            String workflowCode, String workflowVersion, String stageCode);
}
