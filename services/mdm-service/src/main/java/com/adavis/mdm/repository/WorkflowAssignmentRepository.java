package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.WorkflowAssignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowAssignmentRepository extends MongoRepository<WorkflowAssignment, String> {

    Optional<WorkflowAssignment> findByAssignmentId(String assignmentId);

    List<WorkflowAssignment> findByWorkflowCode(String workflowCode);

    List<WorkflowAssignment> findByWorkflowCodeAndStageCode(String workflowCode, String stageCode);

    Optional<WorkflowAssignment> findByWorkflowCodeAndStageCodeAndRoleCode(
            String workflowCode, String stageCode, String roleCode);
}
