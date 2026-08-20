package com.adavis.mdm.repository;

import com.adavis.mdm.model.entity.WorkflowDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepository extends MongoRepository<WorkflowDefinition, String> {

    Optional<WorkflowDefinition> findByWorkflowId(String workflowId);

    List<WorkflowDefinition> findByWorkflowCode(String workflowCode);

    Optional<WorkflowDefinition> findByWorkflowCodeAndVersion(String workflowCode, String version);

    List<WorkflowDefinition> findByTenantId(String tenantId);

    List<WorkflowDefinition> findByModuleAndEntityAndStatus(String module, String entity, String status);

    Optional<WorkflowDefinition> findFirstByWorkflowCodeAndStatus(String workflowCode, String status);

    Optional<WorkflowDefinition> findFirstByModuleAndEntityAndStatusAndTenantId(
            String module, String entity, String status, String tenantId);
}
