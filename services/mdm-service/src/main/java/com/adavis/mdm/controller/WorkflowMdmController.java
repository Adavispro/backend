package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.model.entity.*;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.service.WorkflowMdmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/workflows")
@RequiredArgsConstructor
public class WorkflowMdmController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final WorkflowMdmService workflowMdmService;
    private final InternalRequestValidator internalRequestValidator;

    // ============================================
    // WORKFLOW DEFINITION ENDPOINTS
    // ============================================

    @PostMapping("/definitions")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> createDefinition(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowDefinition definition) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        WorkflowDefinition created = workflowMdmService.createDefinition(definition);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Workflow definition created successfully", created));
    }

    @GetMapping("/definitions")
    public ResponseEntity<ApiResponse<List<WorkflowDefinition>>> getDefinitions(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getAllDefinitions(tenantId, status)));
    }

    @GetMapping("/definitions/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> getDefinitionById(@PathVariable String workflowId) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getDefinitionById(workflowId)));
    }

    @PutMapping("/definitions/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> updateDefinition(
            @PathVariable String workflowId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowDefinition definition) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        WorkflowDefinition updated = workflowMdmService.updateDefinition(workflowId, definition);
        return ResponseEntity.ok(ApiResponse.success("Workflow definition updated successfully", updated));
    }

    @DeleteMapping("/definitions/{workflowId}")
    public ResponseEntity<ApiResponse<Void>> deleteDefinition(
            @PathVariable String workflowId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        workflowMdmService.deleteDefinition(workflowId);
        return ResponseEntity.ok(ApiResponse.successMessage("Workflow definition deleted successfully"));
    }

    @PostMapping("/definitions/{workflowCode}/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateWorkflow(
            @PathVariable String workflowCode,
            @RequestParam(required = false) String version) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.validateWorkflow(workflowCode, version)));
    }

    @PostMapping("/definitions/{workflowId}/activate")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> activateWorkflow(
            @PathVariable String workflowId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        WorkflowDefinition activated = workflowMdmService.activateWorkflow(workflowId);
        return ResponseEntity.ok(ApiResponse.success("Workflow version activated successfully", activated));
    }

    @PostMapping("/definitions/{workflowId}/retire")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> retireWorkflow(
            @PathVariable String workflowId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        WorkflowDefinition retired = workflowMdmService.retireWorkflow(workflowId);
        return ResponseEntity.ok(ApiResponse.success("Workflow version retired successfully", retired));
    }

    // ============================================
    // STAGES ENDPOINTS
    // ============================================

    @PostMapping("/stages")
    public ResponseEntity<ApiResponse<WorkflowStage>> createStage(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowStage stage) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Workflow stage created", workflowMdmService.createStage(stage)));
    }

    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<WorkflowStage>>> getStages(
            @RequestParam String workflowCode,
            @RequestParam(required = false) String version) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getStagesByWorkflow(workflowCode, version)));
    }

    @PutMapping("/stages/{stageId}")
    public ResponseEntity<ApiResponse<WorkflowStage>> updateStage(
            @PathVariable String stageId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowStage stage) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.ok(ApiResponse.success("Workflow stage updated", workflowMdmService.updateStage(stageId, stage)));
    }

    @DeleteMapping("/stages/{stageId}")
    public ResponseEntity<ApiResponse<Void>> deleteStage(
            @PathVariable String stageId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        workflowMdmService.deleteStage(stageId);
        return ResponseEntity.ok(ApiResponse.successMessage("Workflow stage deleted"));
    }

    // ============================================
    // ACTIONS ENDPOINTS
    // ============================================

    @PostMapping("/actions")
    public ResponseEntity<ApiResponse<WorkflowAction>> createAction(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowAction action) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Workflow action created", workflowMdmService.createAction(action)));
    }

    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<WorkflowAction>>> getActions() {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getAllActions()));
    }

    @PutMapping("/actions/{actionId}")
    public ResponseEntity<ApiResponse<WorkflowAction>> updateAction(
            @PathVariable String actionId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowAction action) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.ok(ApiResponse.success("Workflow action updated", workflowMdmService.updateAction(actionId, action)));
    }

    @DeleteMapping("/actions/{actionId}")
    public ResponseEntity<ApiResponse<Void>> deleteAction(
            @PathVariable String actionId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        workflowMdmService.deleteAction(actionId);
        return ResponseEntity.ok(ApiResponse.successMessage("Workflow action deleted"));
    }

    // ============================================
    // TRANSITIONS ENDPOINTS
    // ============================================

    @PostMapping("/transitions")
    public ResponseEntity<ApiResponse<WorkflowTransition>> createTransition(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowTransition transition) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Workflow transition created", workflowMdmService.createTransition(transition)));
    }

    @GetMapping("/transitions")
    public ResponseEntity<ApiResponse<List<WorkflowTransition>>> getTransitions(
            @RequestParam String workflowCode,
            @RequestParam(required = false) String version) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getTransitionsByWorkflow(workflowCode, version)));
    }

    @PutMapping("/transitions/{transitionId}")
    public ResponseEntity<ApiResponse<WorkflowTransition>> updateTransition(
            @PathVariable String transitionId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowTransition transition) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.ok(ApiResponse.success("Workflow transition updated", workflowMdmService.updateTransition(transitionId, transition)));
    }

    @DeleteMapping("/transitions/{transitionId}")
    public ResponseEntity<ApiResponse<Void>> deleteTransition(
            @PathVariable String transitionId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        workflowMdmService.deleteTransition(transitionId);
        return ResponseEntity.ok(ApiResponse.successMessage("Workflow transition deleted"));
    }

    // ============================================
    // ASSIGNMENTS ENDPOINTS
    // ============================================

    @PostMapping("/assignments")
    public ResponseEntity<ApiResponse<WorkflowAssignment>> createAssignment(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowAssignment assignment) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Workflow assignment created", workflowMdmService.createAssignment(assignment)));
    }

    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<WorkflowAssignment>>> getAssignments(@RequestParam String workflowCode) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getAssignmentsByWorkflow(workflowCode)));
    }

    @PutMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<WorkflowAssignment>> updateAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @Valid @RequestBody WorkflowAssignment assignment) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        return ResponseEntity.ok(ApiResponse.success("Workflow assignment updated", workflowMdmService.updateAssignment(assignmentId, assignment)));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        workflowMdmService.deleteAssignment(assignmentId);
        return ResponseEntity.ok(ApiResponse.successMessage("Workflow assignment deleted"));
    }

    // ============================================
    // ACTIVE WORKFLOW CONFIGURATION BUNDLE
    // ============================================

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkflowConfig(
            @RequestParam String workflowCode,
            @RequestParam(required = false) String version) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getFullWorkflowConfiguration(workflowCode, version)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveWorkflow(
            @RequestParam String module,
            @RequestParam String entity,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String plantId) {
        return ResponseEntity.ok(ApiResponse.success(workflowMdmService.getActiveWorkflowForEntity(module, entity, tenantId, plantId)));
    }
}
