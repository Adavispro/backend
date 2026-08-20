package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ResourceNotFoundException;
import com.adavis.mdm.model.entity.*;
import com.adavis.mdm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowMdmService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowActionRepository actionRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowAssignmentRepository assignmentRepository;
    private final BusinessIdGeneratorService idGeneratorService;

    // ============================================
    // WORKFLOW DEFINITION CRUD
    // ============================================

    public WorkflowDefinition createDefinition(WorkflowDefinition definition) {
        if (definition.getWorkflowCode() == null || definition.getWorkflowCode().isBlank()) {
            throw new BusinessException("Workflow code is required", "VALIDATION_ERROR");
        }
        if (definition.getModule() == null || definition.getModule().isBlank()) {
            throw new BusinessException("Module is required", "VALIDATION_ERROR");
        }
        if (definition.getEntity() == null || definition.getEntity().isBlank()) {
            throw new BusinessException("Entity is required", "VALIDATION_ERROR");
        }

        String version = definition.getVersion() != null && !definition.getVersion().isBlank() 
                ? definition.getVersion().trim() : "1.0.0";
        definition.setVersion(version);

        Optional<WorkflowDefinition> existing = definitionRepository.findByWorkflowCodeAndVersion(
                definition.getWorkflowCode(), version);
        if (existing.isPresent()) {
            throw new BusinessException("Workflow definition with code '" + definition.getWorkflowCode() 
                    + "' and version '" + version + "' already exists", "DUPLICATE_RESOURCE");
        }

        String workflowId = definition.getWorkflowId();
        if (workflowId == null || workflowId.isBlank()) {
            try {
                workflowId = idGeneratorService.nextId("mdm_workflow_definitions", "workflowId", "WFD-", 4);
            } catch (Exception e) {
                workflowId = "WFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        }
        definition.setWorkflowId(workflowId);

        if (definition.getStatus() == null || definition.getStatus().isBlank()) {
            definition.setStatus("DRAFT");
        }
        if (definition.getIsActive() == null) {
            definition.setIsActive(true);
        }

        Instant now = Instant.now();
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);

        log.info("Creating workflow definition: id={}, code={}, version={}, status={}",
                workflowId, definition.getWorkflowCode(), version, definition.getStatus());
        return definitionRepository.save(definition);
    }

    public List<WorkflowDefinition> getAllDefinitions(String tenantId, String status) {
        if (status != null && !status.isBlank()) {
            if (tenantId != null && !tenantId.isBlank()) {
                return definitionRepository.findByTenantId(tenantId).stream()
                        .filter(d -> status.equalsIgnoreCase(d.getStatus()))
                        .toList();
            }
            return definitionRepository.findAll().stream()
                    .filter(d -> status.equalsIgnoreCase(d.getStatus()))
                    .toList();
        }
        if (tenantId != null && !tenantId.isBlank()) {
            return definitionRepository.findByTenantId(tenantId);
        }
        return definitionRepository.findAll();
    }

    public WorkflowDefinition getDefinitionById(String workflowId) {
        return definitionRepository.findByWorkflowId(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + workflowId));
    }

    public WorkflowDefinition updateDefinition(String workflowId, WorkflowDefinition updated) {
        WorkflowDefinition existing = getDefinitionById(workflowId);

        if ("RETIRED".equalsIgnoreCase(existing.getStatus())) {
            throw new BusinessException("Cannot update a retired workflow definition", "INVALID_STATE");
        }

        if (updated.getWorkflowName() != null) existing.setWorkflowName(updated.getWorkflowName());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getModule() != null) existing.setModule(updated.getModule());
        if (updated.getEntity() != null) existing.setEntity(updated.getEntity());
        if (updated.getStageCodes() != null) existing.setStageCodes(updated.getStageCodes());
        if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

        existing.setUpdatedAt(Instant.now());
        return definitionRepository.save(existing);
    }

    public void deleteDefinition(String workflowId) {
        WorkflowDefinition existing = getDefinitionById(workflowId);
        if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
            throw new BusinessException("Cannot delete an active workflow definition. Retire it first.", "INVALID_STATE");
        }
        definitionRepository.delete(existing);
    }

    // ============================================
    // WORKFLOW LIFECYCLE: VALIDATE, ACTIVATE, RETIRE
    // ============================================

    public Map<String, Object> validateWorkflow(String workflowCode, String version) {
        WorkflowDefinition definition = definitionRepository.findByWorkflowCodeAndVersion(workflowCode, version)
                .orElseGet(() -> definitionRepository.findFirstByWorkflowCodeAndStatus(workflowCode, "ACTIVE")
                        .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found for code: " + workflowCode)));

        List<String> validationErrors = new ArrayList<>();
        List<WorkflowStage> stages = stageRepository.findByWorkflowCode(workflowCode);
        if (stages.isEmpty()) {
            validationErrors.add("Workflow has no stages defined.");
        }

        boolean hasInitial = stages.stream().anyMatch(s -> "INITIAL".equalsIgnoreCase(s.getStageType()) || s.getSequence() == 1);
        if (!hasInitial) {
            validationErrors.add("Workflow has no INITIAL stage.");
        }

        List<WorkflowTransition> transitions = transitionRepository.findByWorkflowCode(workflowCode);
        if (transitions.isEmpty()) {
            validationErrors.add("Workflow has no transitions configured.");
        }

        List<WorkflowAssignment> assignments = assignmentRepository.findByWorkflowCode(workflowCode);
        if (assignments.isEmpty()) {
            validationErrors.add("Workflow has no role assignments configured.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowCode", workflowCode);
        result.put("version", definition.getVersion());
        result.put("valid", validationErrors.isEmpty());
        result.put("errors", validationErrors);
        result.put("stagesCount", stages.size());
        result.put("transitionsCount", transitions.size());
        result.put("assignmentsCount", assignments.size());
        return result;
    }

    @Transactional
    public WorkflowDefinition activateWorkflow(String workflowId) {
        WorkflowDefinition target = getDefinitionById(workflowId);

        if ("ACTIVE".equalsIgnoreCase(target.getStatus())) {
            return target;
        }

        Map<String, Object> validation = validateWorkflow(target.getWorkflowCode(), target.getVersion());
        if (!Boolean.TRUE.equals(validation.get("valid"))) {
            throw new BusinessException("Cannot activate invalid workflow definition: " + validation.get("errors"), "VALIDATION_FAILED");
        }

        // Retire any currently active versions for the same module + entity + tenant scope
        List<WorkflowDefinition> activeDefinitions = definitionRepository.findByModuleAndEntityAndStatus(
                target.getModule(), target.getEntity(), "ACTIVE");
        Instant now = Instant.now();

        for (WorkflowDefinition active : activeDefinitions) {
            if (active.getWorkflowId().equals(target.getWorkflowId())) continue;

            boolean tenantMatch = (active.getTenantId() == null && target.getTenantId() == null)
                    || (active.getTenantId() != null && active.getTenantId().equalsIgnoreCase(target.getTenantId()));

            if (tenantMatch) {
                log.info("Retiring previous active workflow version: id={}, code={}, version={}",
                        active.getWorkflowId(), active.getWorkflowCode(), active.getVersion());
                active.setStatus("RETIRED");
                active.setEffectiveTo(now);
                active.setUpdatedAt(now);
                definitionRepository.save(active);
            }
        }

        target.setStatus("ACTIVE");
        target.setEffectiveFrom(now);
        target.setEffectiveTo(null);
        target.setUpdatedAt(now);

        log.info("Activated workflow definition: id={}, code={}, version={}",
                target.getWorkflowId(), target.getWorkflowCode(), target.getVersion());
        return definitionRepository.save(target);
    }

    public WorkflowDefinition retireWorkflow(String workflowId) {
        WorkflowDefinition target = getDefinitionById(workflowId);
        Instant now = Instant.now();
        target.setStatus("RETIRED");
        target.setEffectiveTo(now);
        target.setUpdatedAt(now);
        return definitionRepository.save(target);
    }

    // ============================================
    // STAGES CRUD
    // ============================================

    public WorkflowStage createStage(WorkflowStage stage) {
        if (stage.getStageCode() == null || stage.getStageCode().isBlank()) {
            throw new BusinessException("Stage code is required", "VALIDATION_ERROR");
        }
        if (stage.getWorkflowCode() == null || stage.getWorkflowCode().isBlank()) {
            throw new BusinessException("Workflow code is required", "VALIDATION_ERROR");
        }

        String stageId = stage.getStageId();
        if (stageId == null || stageId.isBlank()) {
            try {
                stageId = idGeneratorService.nextId("mdm_workflow_stages", "stageId", "WFS-", 4);
            } catch (Exception e) {
                stageId = "WFS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        }
        stage.setStageId(stageId);
        if (stage.getIsActive() == null) stage.setIsActive(true);

        Instant now = Instant.now();
        stage.setCreatedAt(now);
        stage.setUpdatedAt(now);
        return stageRepository.save(stage);
    }

    public List<WorkflowStage> getStagesByWorkflow(String workflowCode, String version) {
        if (version != null && !version.isBlank()) {
            return stageRepository.findByWorkflowCodeAndWorkflowVersion(workflowCode, version);
        }
        return stageRepository.findByWorkflowCode(workflowCode);
    }

    public WorkflowStage updateStage(String stageId, WorkflowStage updated) {
        WorkflowStage existing = stageRepository.findByStageId(stageId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow stage not found: " + stageId));

        if (updated.getStageName() != null) existing.setStageName(updated.getStageName());
        if (updated.getSequence() != null) existing.setSequence(updated.getSequence());
        if (updated.getAssignedRole() != null) existing.setAssignedRole(updated.getAssignedRole());
        if (updated.getStageType() != null) existing.setStageType(updated.getStageType());
        if (updated.getEntryStatus() != null) existing.setEntryStatus(updated.getEntryStatus());
        if (updated.getExitStatus() != null) existing.setExitStatus(updated.getExitStatus());
        if (updated.getAllowedActionCodes() != null) existing.setAllowedActionCodes(updated.getAllowedActionCodes());
        if (updated.getIsMandatory() != null) existing.setIsMandatory(updated.getIsMandatory());
        if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

        existing.setUpdatedAt(Instant.now());
        return stageRepository.save(existing);
    }

    public void deleteStage(String stageId) {
        WorkflowStage stage = stageRepository.findByStageId(stageId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow stage not found: " + stageId));
        stageRepository.delete(stage);
    }

    // ============================================
    // ACTIONS CRUD
    // ============================================

    public WorkflowAction createAction(WorkflowAction action) {
        if (action.getActionCode() == null || action.getActionCode().isBlank()) {
            throw new BusinessException("Action code is required", "VALIDATION_ERROR");
        }

        String actionId = action.getActionId();
        if (actionId == null || actionId.isBlank()) {
            try {
                actionId = idGeneratorService.nextId("mdm_workflow_actions", "actionId", "WFA-", 4);
            } catch (Exception e) {
                actionId = "WFA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        }
        action.setActionId(actionId);
        if (action.getIsActive() == null) action.setIsActive(true);
        if (action.getRequiresEsign() == null) action.setRequiresEsign(true);
        if (action.getRequiresConfirmation() == null) action.setRequiresConfirmation(true);

        Instant now = Instant.now();
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        return actionRepository.save(action);
    }

    public List<WorkflowAction> getAllActions() {
        return actionRepository.findAll();
    }

    public WorkflowAction updateAction(String actionId, WorkflowAction updated) {
        WorkflowAction existing = actionRepository.findByActionId(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow action not found: " + actionId));

        if (updated.getActionName() != null) existing.setActionName(updated.getActionName());
        if (updated.getDisplayName() != null) existing.setDisplayName(updated.getDisplayName());
        if (updated.getActionType() != null) existing.setActionType(updated.getActionType());
        if (updated.getApplicableRole() != null) existing.setApplicableRole(updated.getApplicableRole());
        if (updated.getRequiresEsign() != null) existing.setRequiresEsign(updated.getRequiresEsign());
        if (updated.getRequiresComment() != null) existing.setRequiresComment(updated.getRequiresComment());
        if (updated.getRequiresJustification() != null) existing.setRequiresJustification(updated.getRequiresJustification());
        if (updated.getRequiresUserSelection() != null) existing.setRequiresUserSelection(updated.getRequiresUserSelection());
        if (updated.getRequiresConfirmation() != null) existing.setRequiresConfirmation(updated.getRequiresConfirmation());
        if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

        existing.setUpdatedAt(Instant.now());
        return actionRepository.save(existing);
    }

    public void deleteAction(String actionId) {
        WorkflowAction action = actionRepository.findByActionId(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow action not found: " + actionId));
        actionRepository.delete(action);
    }

    // ============================================
    // TRANSITIONS CRUD
    // ============================================

    public WorkflowTransition createTransition(WorkflowTransition transition) {
        if (transition.getWorkflowCode() == null || transition.getWorkflowCode().isBlank()) {
            throw new BusinessException("Workflow code is required", "VALIDATION_ERROR");
        }
        if (transition.getFromStageCode() == null || transition.getActionCode() == null) {
            throw new BusinessException("fromStageCode and actionCode are required", "VALIDATION_ERROR");
        }

        String transitionId = transition.getTransitionId();
        if (transitionId == null || transitionId.isBlank()) {
            try {
                transitionId = idGeneratorService.nextId("mdm_workflow_transitions", "transitionId", "WFT-", 4);
            } catch (Exception e) {
                transitionId = "WFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        }
        transition.setTransitionId(transitionId);
        if (transition.getIsActive() == null) transition.setIsActive(true);

        Instant now = Instant.now();
        transition.setCreatedAt(now);
        transition.setUpdatedAt(now);
        return transitionRepository.save(transition);
    }

    public List<WorkflowTransition> getTransitionsByWorkflow(String workflowCode, String version) {
        if (version != null && !version.isBlank()) {
            return transitionRepository.findByWorkflowCodeAndWorkflowVersion(workflowCode, version);
        }
        return transitionRepository.findByWorkflowCode(workflowCode);
    }

    public WorkflowTransition updateTransition(String transitionId, WorkflowTransition updated) {
        WorkflowTransition existing = transitionRepository.findByTransitionId(transitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow transition not found: " + transitionId));

        if (updated.getToStageCode() != null) existing.setToStageCode(updated.getToStageCode());
        if (updated.getResultingStatus() != null) existing.setResultingStatus(updated.getResultingStatus());
        if (updated.getReturnStageCode() != null) existing.setReturnStageCode(updated.getReturnStageCode());
        if (updated.getCondition() != null) existing.setCondition(updated.getCondition());
        if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

        existing.setUpdatedAt(Instant.now());
        return transitionRepository.save(existing);
    }

    public void deleteTransition(String transitionId) {
        WorkflowTransition transition = transitionRepository.findByTransitionId(transitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow transition not found: " + transitionId));
        transitionRepository.delete(transition);
    }

    // ============================================
    // ASSIGNMENTS CRUD
    // ============================================

    public WorkflowAssignment createAssignment(WorkflowAssignment assignment) {
        if (assignment.getWorkflowCode() == null || assignment.getStageCode() == null) {
            throw new BusinessException("Workflow code and stage code are required", "VALIDATION_ERROR");
        }

        String assignmentId = assignment.getAssignmentId();
        if (assignmentId == null || assignmentId.isBlank()) {
            try {
                assignmentId = idGeneratorService.nextId("mdm_workflow_assignments", "assignmentId", "WFAS-", 4);
            } catch (Exception e) {
                assignmentId = "WFAS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        }
        assignment.setAssignmentId(assignmentId);
        if (assignment.getIsActive() == null) assignment.setIsActive(true);

        Instant now = Instant.now();
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        return assignmentRepository.save(assignment);
    }

    public List<WorkflowAssignment> getAssignmentsByWorkflow(String workflowCode) {
        return assignmentRepository.findByWorkflowCode(workflowCode);
    }

    public WorkflowAssignment updateAssignment(String assignmentId, WorkflowAssignment updated) {
        WorkflowAssignment existing = assignmentRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow assignment not found: " + assignmentId));

        if (updated.getRoleCode() != null) existing.setRoleCode(updated.getRoleCode());
        if (updated.getGroupId() != null) existing.setGroupId(updated.getGroupId());
        if (updated.getEligibleUserRule() != null) existing.setEligibleUserRule(updated.getEligibleUserRule());
        if (updated.getAssignmentRule() != null) existing.setAssignmentRule(updated.getAssignmentRule());
        if (updated.getDepartmentId() != null) existing.setDepartmentId(updated.getDepartmentId());
        if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

        existing.setUpdatedAt(Instant.now());
        return assignmentRepository.save(existing);
    }

    public void deleteAssignment(String assignmentId) {
        WorkflowAssignment assignment = assignmentRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow assignment not found: " + assignmentId));
        assignmentRepository.delete(assignment);
    }

    // ============================================
    // AGGREGATE WORKFLOW CONFIGURATION
    // ============================================

    public Map<String, Object> getFullWorkflowConfiguration(String workflowCode, String version) {
        WorkflowDefinition definition;
        if (version != null && !version.isBlank()) {
            definition = definitionRepository.findByWorkflowCodeAndVersion(workflowCode, version)
                    .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + workflowCode + " v" + version));
        } else {
            definition = definitionRepository.findFirstByWorkflowCodeAndStatus(workflowCode, "ACTIVE")
                    .orElseGet(() -> definitionRepository.findByWorkflowCode(workflowCode).stream().findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + workflowCode)));
        }

        List<WorkflowStage> stages = stageRepository.findByWorkflowCode(workflowCode);
        stages.sort(Comparator.comparingInt(s -> s.getSequence() != null ? s.getSequence() : 0));

        List<WorkflowTransition> transitions = transitionRepository.findByWorkflowCode(workflowCode);
        List<WorkflowAssignment> assignments = assignmentRepository.findByWorkflowCode(workflowCode);
        List<WorkflowAction> actions = actionRepository.findAll();

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("definition", definition);
        bundle.put("stages", stages);
        bundle.put("actions", actions);
        bundle.put("transitions", transitions);
        bundle.put("assignments", assignments);
        return bundle;
    }

    public Map<String, Object> getActiveWorkflowForEntity(String module, String entity, String tenantId, String plantId) {
        List<WorkflowDefinition> candidates = definitionRepository.findByModuleAndEntityAndStatus(module, entity, "ACTIVE");

        WorkflowDefinition matched = null;
        for (WorkflowDefinition d : candidates) {
            boolean tenantMatch = (d.getTenantId() == null || d.getTenantId().isBlank())
                    || (tenantId != null && tenantId.equalsIgnoreCase(d.getTenantId()));
            boolean plantMatch = (d.getPlantId() == null || d.getPlantId().isBlank())
                    || (plantId != null && plantId.equalsIgnoreCase(d.getPlantId()));

            if (tenantMatch && plantMatch) {
                matched = d;
                break;
            }
        }

        if (matched == null && !candidates.isEmpty()) {
            matched = candidates.get(0);
        }

        if (matched == null) {
            throw new ResourceNotFoundException("No active workflow configuration found for module=" + module + ", entity=" + entity);
        }

        return getFullWorkflowConfiguration(matched.getWorkflowCode(), matched.getVersion());
    }
}
