package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ForbiddenException;
import com.adavis.common.exception.UnauthorizedException;
import com.adavis.iiot.model.WorkflowActionHistory;
import com.adavis.iiot.model.WorkflowInstance;
import com.adavis.security.PasswordEncoderConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicWorkflowEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicWorkflowEngine.class);

    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;
    private final BatchPdfGeneratorService batchPdfGeneratorService;

    public static final String MODULE_IIOT = "IIOT";
    public static final String ENTITY_BATCH_STAGE = "BATCH_STAGE";

    private static final String BATCH_SUMMARY_COLLECTION = "iiot_batch_summary";
    private static final String AUDIT_COLLECTION = "iiot_workflow_audit_trail";
    private static final String INSTANCE_COLLECTION = "iiot_workflow_instances";
    private static final String HISTORY_COLLECTION = "iiot_workflow_action_history";

    private static final String DEFINITIONS_COLLECTION = "mdm_workflow_definitions";
    private static final String STAGES_COLLECTION = "mdm_workflow_stages";
    private static final String ACTIONS_COLLECTION = "mdm_workflow_actions";
    private static final String TRANSITIONS_COLLECTION = "mdm_workflow_transitions";
    private static final String ASSIGNMENTS_COLLECTION = "mdm_workflow_assignments";
    private static final String CREDENTIALS_COLLECTION = "mdm_user_auth_credentials";

    // Canonical standard role codes
    public static final String ROLE_ADMIN = "PLATFORM_SUPER_ADMIN";
    public static final String ROLE_OPERATOR = "PRODUCTION_OPERATOR";
    public static final String ROLE_REVIEWER = "PRODUCTION_REVIEWER";
    public static final String ROLE_APPROVER = "QA_APPROVER";
    public static final String ROLE_SUPERVISOR = "SHIFT_SUPERVISOR";

    public static class AllowedActionDto {
        private String actionCode;
        private String actionName;
        private String displayName;
        private String actionType;
        private String applicableRole;
        private Boolean requiresEsign;
        private Boolean requiresComment;
        private Boolean requiresJustification;
        private Boolean requiresAdditionalInfo;
        private Boolean requiresResponse;
        private Boolean requiresUserSelection;
        private Boolean requiresConfirmation;
        private String fromStageCode;
        private String toStageCode;
        private String resultingStatus;
        private String returnStageCode;

        public AllowedActionDto() {}

        public static AllowedActionDtoBuilder builder() { return new AllowedActionDtoBuilder(); }

        public static class AllowedActionDtoBuilder {
            private String actionCode;
            private String actionName;
            private String displayName;
            private String actionType;
            private String applicableRole;
            private Boolean requiresEsign;
            private Boolean requiresComment;
            private Boolean requiresJustification;
            private Boolean requiresAdditionalInfo;
            private Boolean requiresResponse;
            private Boolean requiresUserSelection;
            private Boolean requiresConfirmation;
            private String fromStageCode;
            private String toStageCode;
            private String resultingStatus;
            private String returnStageCode;

            public AllowedActionDtoBuilder actionCode(String v) { this.actionCode = v; return this; }
            public AllowedActionDtoBuilder actionName(String v) { this.actionName = v; return this; }
            public AllowedActionDtoBuilder displayName(String v) { this.displayName = v; return this; }
            public AllowedActionDtoBuilder actionType(String v) { this.actionType = v; return this; }
            public AllowedActionDtoBuilder applicableRole(String v) { this.applicableRole = v; return this; }
            public AllowedActionDtoBuilder requiresEsign(Boolean v) { this.requiresEsign = v; return this; }
            public AllowedActionDtoBuilder requiresComment(Boolean v) { this.requiresComment = v; return this; }
            public AllowedActionDtoBuilder requiresJustification(Boolean v) { this.requiresJustification = v; return this; }
            public AllowedActionDtoBuilder requiresAdditionalInfo(Boolean v) { this.requiresAdditionalInfo = v; return this; }
            public AllowedActionDtoBuilder requiresResponse(Boolean v) { this.requiresResponse = v; return this; }
            public AllowedActionDtoBuilder requiresUserSelection(Boolean v) { this.requiresUserSelection = v; return this; }
            public AllowedActionDtoBuilder requiresConfirmation(Boolean v) { this.requiresConfirmation = v; return this; }
            public AllowedActionDtoBuilder fromStageCode(String v) { this.fromStageCode = v; return this; }
            public AllowedActionDtoBuilder toStageCode(String v) { this.toStageCode = v; return this; }
            public AllowedActionDtoBuilder resultingStatus(String v) { this.resultingStatus = v; return this; }
            public AllowedActionDtoBuilder returnStageCode(String v) { this.returnStageCode = v; return this; }

            public AllowedActionDto build() {
                AllowedActionDto dto = new AllowedActionDto();
                dto.actionCode = this.actionCode;
                dto.actionName = this.actionName;
                dto.displayName = this.displayName;
                dto.actionType = this.actionType;
                dto.applicableRole = this.applicableRole;
                dto.requiresEsign = this.requiresEsign;
                dto.requiresComment = this.requiresComment;
                dto.requiresJustification = this.requiresJustification;
                dto.requiresAdditionalInfo = this.requiresAdditionalInfo;
                dto.requiresResponse = this.requiresResponse;
                dto.requiresUserSelection = this.requiresUserSelection;
                dto.requiresConfirmation = this.requiresConfirmation;
                dto.fromStageCode = this.fromStageCode;
                dto.toStageCode = this.toStageCode;
                dto.resultingStatus = this.resultingStatus;
                dto.returnStageCode = this.returnStageCode;
                return dto;
            }
        }

        public String getActionCode() { return actionCode; }
        public void setActionCode(String actionCode) { this.actionCode = actionCode; }
        public String getActionName() { return actionName; }
        public void setActionName(String actionName) { this.actionName = actionName; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getApplicableRole() { return applicableRole; }
        public void setApplicableRole(String applicableRole) { this.applicableRole = applicableRole; }
        public Boolean getRequiresEsign() { return requiresEsign; }
        public void setRequiresEsign(Boolean requiresEsign) { this.requiresEsign = requiresEsign; }
        public Boolean getRequiresComment() { return requiresComment; }
        public void setRequiresComment(Boolean requiresComment) { this.requiresComment = requiresComment; }
        public Boolean getRequiresJustification() { return requiresJustification; }
        public void setRequiresJustification(Boolean requiresJustification) { this.requiresJustification = requiresJustification; }
        public Boolean getRequiresAdditionalInfo() { return requiresAdditionalInfo; }
        public void setRequiresAdditionalInfo(Boolean requiresAdditionalInfo) { this.requiresAdditionalInfo = requiresAdditionalInfo; }
        public Boolean getRequiresResponse() { return requiresResponse; }
        public void setRequiresResponse(Boolean requiresResponse) { this.requiresResponse = requiresResponse; }
        public Boolean getRequiresUserSelection() { return requiresUserSelection; }
        public void setRequiresUserSelection(Boolean requiresUserSelection) { this.requiresUserSelection = requiresUserSelection; }
        public Boolean getRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(Boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
        public String getFromStageCode() { return fromStageCode; }
        public void setFromStageCode(String fromStageCode) { this.fromStageCode = fromStageCode; }
        public String getToStageCode() { return toStageCode; }
        public void setToStageCode(String toStageCode) { this.toStageCode = toStageCode; }
        public String getResultingStatus() { return resultingStatus; }
        public void setResultingStatus(String resultingStatus) { this.resultingStatus = resultingStatus; }
        public String getReturnStageCode() { return returnStageCode; }
        public void setReturnStageCode(String returnStageCode) { this.returnStageCode = returnStageCode; }
    }

    public static class ActionExecutionRequest {
        private String userId;
        private String userRole;
        private String tenantId;
        private String plantId;
        private String batchNo;
        private String lotNo;
        private String equipmentCode;
        private String actionCode;
        private String password;
        private String comments;
        private String justification;
        private String additionalInformation;
        private String responseNotes;
        private String supervisorName;
        private String esignatureReason;

        public ActionExecutionRequest() {}

        public static ActionExecutionRequestBuilder builder() { return new ActionExecutionRequestBuilder(); }

        public static class ActionExecutionRequestBuilder {
            private String userId;
            private String userRole;
            private String tenantId;
            private String plantId;
            private String batchNo;
            private String lotNo;
            private String equipmentCode;
            private String actionCode;
            private String password;
            private String comments;
            private String justification;
            private String additionalInformation;
            private String responseNotes;
            private String supervisorName;
            private String esignatureReason;

            public ActionExecutionRequestBuilder userId(String v) { this.userId = v; return this; }
            public ActionExecutionRequestBuilder userRole(String v) { this.userRole = v; return this; }
            public ActionExecutionRequestBuilder tenantId(String v) { this.tenantId = v; return this; }
            public ActionExecutionRequestBuilder plantId(String v) { this.plantId = v; return this; }
            public ActionExecutionRequestBuilder batchNo(String v) { this.batchNo = v; return this; }
            public ActionExecutionRequestBuilder lotNo(String v) { this.lotNo = v; return this; }
            public ActionExecutionRequestBuilder equipmentCode(String v) { this.equipmentCode = v; return this; }
            public ActionExecutionRequestBuilder actionCode(String v) { this.actionCode = v; return this; }
            public ActionExecutionRequestBuilder password(String v) { this.password = v; return this; }
            public ActionExecutionRequestBuilder comments(String v) { this.comments = v; return this; }
            public ActionExecutionRequestBuilder justification(String v) { this.justification = v; return this; }
            public ActionExecutionRequestBuilder additionalInformation(String v) { this.additionalInformation = v; return this; }
            public ActionExecutionRequestBuilder responseNotes(String v) { this.responseNotes = v; return this; }
            public ActionExecutionRequestBuilder supervisorName(String v) { this.supervisorName = v; return this; }
            public ActionExecutionRequestBuilder esignatureReason(String v) { this.esignatureReason = v; return this; }

            public ActionExecutionRequest build() {
                ActionExecutionRequest r = new ActionExecutionRequest();
                r.userId = this.userId;
                r.userRole = this.userRole;
                r.tenantId = this.tenantId;
                r.plantId = this.plantId;
                r.batchNo = this.batchNo;
                r.lotNo = this.lotNo;
                r.equipmentCode = this.equipmentCode;
                r.actionCode = this.actionCode;
                r.password = this.password;
                r.comments = this.comments;
                r.justification = this.justification;
                r.additionalInformation = this.additionalInformation;
                r.responseNotes = this.responseNotes;
                r.supervisorName = this.supervisorName;
                r.esignatureReason = this.esignatureReason;
                return r;
            }
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPlantId() { return plantId; }
        public void setPlantId(String plantId) { this.plantId = plantId; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getLotNo() { return lotNo; }
        public void setLotNo(String lotNo) { this.lotNo = lotNo; }
        public String getEquipmentCode() { return equipmentCode; }
        public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
        public String getActionCode() { return actionCode; }
        public void setActionCode(String actionCode) { this.actionCode = actionCode; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
        public String getJustification() { return justification; }
        public void setJustification(String justification) { this.justification = justification; }
        public String getAdditionalInformation() { return additionalInformation; }
        public void setAdditionalInformation(String additionalInformation) { this.additionalInformation = additionalInformation; }
        public String getResponseNotes() { return responseNotes; }
        public void setResponseNotes(String responseNotes) { this.responseNotes = responseNotes; }
        public String getSupervisorName() { return supervisorName; }
        public void setSupervisorName(String supervisorName) { this.supervisorName = supervisorName; }
        public String getEsignatureReason() { return esignatureReason; }
        public void setEsignatureReason(String esignatureReason) { this.esignatureReason = esignatureReason; }
    }

    public static class BulkActionItem {
        private String batchNo;
        private String lotNo;
        private String equipmentCode;

        public BulkActionItem() {}
        public BulkActionItem(String batchNo, String lotNo, String equipmentCode) {
            this.batchNo = batchNo;
            this.lotNo = lotNo;
            this.equipmentCode = equipmentCode;
        }

        public static BulkActionItemBuilder builder() { return new BulkActionItemBuilder(); }

        public static class BulkActionItemBuilder {
            private String batchNo;
            private String lotNo;
            private String equipmentCode;

            public BulkActionItemBuilder batchNo(String v) { this.batchNo = v; return this; }
            public BulkActionItemBuilder lotNo(String v) { this.lotNo = v; return this; }
            public BulkActionItemBuilder equipmentCode(String v) { this.equipmentCode = v; return this; }

            public BulkActionItem build() {
                return new BulkActionItem(batchNo, lotNo, equipmentCode);
            }
        }

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getLotNo() { return lotNo; }
        public void setLotNo(String lotNo) { this.lotNo = lotNo; }
        public String getEquipmentCode() { return equipmentCode; }
        public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
    }

    public static class BulkActionExecutionRequest {
        private String userId;
        private String userRole;
        private String tenantId;
        private String plantId;
        private String actionCode;
        private String password;
        private String comments;
        private String justification;
        private String additionalInformation;
        private String responseNotes;
        private String supervisorName;
        private String esignatureReason;
        private List<BulkActionItem> items;

        public BulkActionExecutionRequest() {}

        public static BulkActionExecutionRequestBuilder builder() { return new BulkActionExecutionRequestBuilder(); }

        public static class BulkActionExecutionRequestBuilder {
            private String userId;
            private String userRole;
            private String tenantId;
            private String plantId;
            private String actionCode;
            private String password;
            private String comments;
            private String justification;
            private String additionalInformation;
            private String responseNotes;
            private String supervisorName;
            private String esignatureReason;
            private List<BulkActionItem> items;

            public BulkActionExecutionRequestBuilder userId(String v) { this.userId = v; return this; }
            public BulkActionExecutionRequestBuilder userRole(String v) { this.userRole = v; return this; }
            public BulkActionExecutionRequestBuilder tenantId(String v) { this.tenantId = v; return this; }
            public BulkActionExecutionRequestBuilder plantId(String v) { this.plantId = v; return this; }
            public BulkActionExecutionRequestBuilder actionCode(String v) { this.actionCode = v; return this; }
            public BulkActionExecutionRequestBuilder password(String v) { this.password = v; return this; }
            public BulkActionExecutionRequestBuilder comments(String v) { this.comments = v; return this; }
            public BulkActionExecutionRequestBuilder justification(String v) { this.justification = v; return this; }
            public BulkActionExecutionRequestBuilder additionalInformation(String v) { this.additionalInformation = v; return this; }
            public BulkActionExecutionRequestBuilder responseNotes(String v) { this.responseNotes = v; return this; }
            public BulkActionExecutionRequestBuilder supervisorName(String v) { this.supervisorName = v; return this; }
            public BulkActionExecutionRequestBuilder esignatureReason(String v) { this.esignatureReason = v; return this; }
            public BulkActionExecutionRequestBuilder items(List<BulkActionItem> v) { this.items = v; return this; }

            public BulkActionExecutionRequest build() {
                BulkActionExecutionRequest req = new BulkActionExecutionRequest();
                req.userId = this.userId;
                req.userRole = this.userRole;
                req.tenantId = this.tenantId;
                req.plantId = this.plantId;
                req.actionCode = this.actionCode;
                req.password = this.password;
                req.comments = this.comments;
                req.justification = this.justification;
                req.additionalInformation = this.additionalInformation;
                req.responseNotes = this.responseNotes;
                req.supervisorName = this.supervisorName;
                req.esignatureReason = this.esignatureReason;
                req.items = this.items;
                return req;
            }
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPlantId() { return plantId; }
        public void setPlantId(String plantId) { this.plantId = plantId; }
        public String getActionCode() { return actionCode; }
        public void setActionCode(String actionCode) { this.actionCode = actionCode; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
        public String getJustification() { return justification; }
        public void setJustification(String justification) { this.justification = justification; }
        public String getAdditionalInformation() { return additionalInformation; }
        public void setAdditionalInformation(String additionalInformation) { this.additionalInformation = additionalInformation; }
        public String getResponseNotes() { return responseNotes; }
        public void setResponseNotes(String responseNotes) { this.responseNotes = responseNotes; }
        public String getSupervisorName() { return supervisorName; }
        public void setSupervisorName(String supervisorName) { this.supervisorName = supervisorName; }
        public String getEsignatureReason() { return esignatureReason; }
        public void setEsignatureReason(String esignatureReason) { this.esignatureReason = esignatureReason; }
        public List<BulkActionItem> getItems() { return items; }
        public void setItems(List<BulkActionItem> items) { this.items = items; }
    }

    public static class BulkSuccessItem {
        private String batchNo;
        private String lotNo;
        private String equipmentCode;
        private String newStatus;
        private String message;

        public BulkSuccessItem() {}
        public BulkSuccessItem(String batchNo, String lotNo, String equipmentCode, String newStatus, String message) {
            this.batchNo = batchNo;
            this.lotNo = lotNo;
            this.equipmentCode = equipmentCode;
            this.newStatus = newStatus;
            this.message = message;
        }

        public static BulkSuccessItemBuilder builder() { return new BulkSuccessItemBuilder(); }

        public static class BulkSuccessItemBuilder {
            private String batchNo;
            private String lotNo;
            private String equipmentCode;
            private String newStatus;
            private String message;

            public BulkSuccessItemBuilder batchNo(String v) { this.batchNo = v; return this; }
            public BulkSuccessItemBuilder lotNo(String v) { this.lotNo = v; return this; }
            public BulkSuccessItemBuilder equipmentCode(String v) { this.equipmentCode = v; return this; }
            public BulkSuccessItemBuilder newStatus(String v) { this.newStatus = v; return this; }
            public BulkSuccessItemBuilder message(String v) { this.message = v; return this; }

            public BulkSuccessItem build() {
                return new BulkSuccessItem(batchNo, lotNo, equipmentCode, newStatus, message);
            }
        }

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getLotNo() { return lotNo; }
        public void setLotNo(String lotNo) { this.lotNo = lotNo; }
        public String getEquipmentCode() { return equipmentCode; }
        public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
        public String getNewStatus() { return newStatus; }
        public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class BulkFailureItem {
        private String batchNo;
        private String lotNo;
        private String equipmentCode;
        private String reason;
        private String errorCode;

        public BulkFailureItem() {}
        public BulkFailureItem(String batchNo, String lotNo, String equipmentCode, String reason, String errorCode) {
            this.batchNo = batchNo;
            this.lotNo = lotNo;
            this.equipmentCode = equipmentCode;
            this.reason = reason;
            this.errorCode = errorCode;
        }

        public static BulkFailureItemBuilder builder() { return new BulkFailureItemBuilder(); }

        public static class BulkFailureItemBuilder {
            private String batchNo;
            private String lotNo;
            private String equipmentCode;
            private String reason;
            private String errorCode;

            public BulkFailureItemBuilder batchNo(String v) { this.batchNo = v; return this; }
            public BulkFailureItemBuilder lotNo(String v) { this.lotNo = v; return this; }
            public BulkFailureItemBuilder equipmentCode(String v) { this.equipmentCode = v; return this; }
            public BulkFailureItemBuilder reason(String v) { this.reason = v; return this; }
            public BulkFailureItemBuilder errorCode(String v) { this.errorCode = v; return this; }

            public BulkFailureItem build() {
                return new BulkFailureItem(batchNo, lotNo, equipmentCode, reason, errorCode);
            }
        }

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getLotNo() { return lotNo; }
        public void setLotNo(String lotNo) { this.lotNo = lotNo; }
        public String getEquipmentCode() { return equipmentCode; }
        public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    }

    public static class BulkExecutionResult {
        private int totalRequested;
        private int successCount;
        private int failureCount;
        private List<BulkSuccessItem> successfulItems;
        private List<BulkFailureItem> failedItems;

        public BulkExecutionResult() {}
        public BulkExecutionResult(int totalRequested, int successCount, int failureCount, List<BulkSuccessItem> successfulItems, List<BulkFailureItem> failedItems) {
            this.totalRequested = totalRequested;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.successfulItems = successfulItems;
            this.failedItems = failedItems;
        }

        public static BulkExecutionResultBuilder builder() { return new BulkExecutionResultBuilder(); }

        public static class BulkExecutionResultBuilder {
            private int totalRequested;
            private int successCount;
            private int failureCount;
            private List<BulkSuccessItem> successfulItems;
            private List<BulkFailureItem> failedItems;

            public BulkExecutionResultBuilder totalRequested(int v) { this.totalRequested = v; return this; }
            public BulkExecutionResultBuilder successCount(int v) { this.successCount = v; return this; }
            public BulkExecutionResultBuilder failureCount(int v) { this.failureCount = v; return this; }
            public BulkExecutionResultBuilder successfulItems(List<BulkSuccessItem> v) { this.successfulItems = v; return this; }
            public BulkExecutionResultBuilder failedItems(List<BulkFailureItem> v) { this.failedItems = v; return this; }

            public BulkExecutionResult build() {
                return new BulkExecutionResult(totalRequested, successCount, failureCount, successfulItems, failedItems);
            }
        }

        public int getTotalRequested() { return totalRequested; }
        public void setTotalRequested(int totalRequested) { this.totalRequested = totalRequested; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailureCount() { return failureCount; }
        public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
        public List<BulkSuccessItem> getSuccessfulItems() { return successfulItems; }
        public void setSuccessfulItems(List<BulkSuccessItem> successfulItems) { this.successfulItems = successfulItems; }
        public List<BulkFailureItem> getFailedItems() { return failedItems; }
        public void setFailedItems(List<BulkFailureItem> failedItems) { this.failedItems = failedItems; }
    }

    // ============================================
    // DYNAMIC ALLOWED ACTIONS RESOLUTION
    // ============================================

    public List<AllowedActionDto> getAllowedActions(
            String userId,
            String userRoleCode,
            String tenantId,
            String plantId,
            String batchNo,
            String lotNo,
            String equipmentCode) {

        userId = userId != null ? userId.trim() : "SYSTEM";
        userRoleCode = userRoleCode != null && !userRoleCode.isBlank() 
                ? userRoleCode.toUpperCase(Locale.ROOT).trim() : resolveUserRoleCode(userId);

        // Find or create runtime instance
        WorkflowInstance instance = getOrCreateWorkflowInstance(
                batchNo, lotNo, equipmentCode, tenantId, plantId, null, userId);

        if (instance == null) {
            return Collections.emptyList();
        }

        String currentStageCode = instance.getCurrentStageCode();
        String currentStatus = instance.getCurrentStatus();

        if (Boolean.TRUE.equals(instance.getIsTerminal()) 
                || "APPROVED".equalsIgnoreCase(currentStatus) 
                || "COMPLETED".equalsIgnoreCase(currentStatus)
                || "REJECTED".equalsIgnoreCase(currentStatus)) {
            return Collections.emptyList();
        }

        String workflowCode = instance.getWorkflowCode();
        String workflowVersion = instance.getWorkflowVersion();

        // Load stage configuration for current stage
        Query stageQ = new Query(Criteria.where("workflowCode").is(workflowCode)
                .and("stageCode").is(currentStageCode));
        if (workflowVersion != null && !workflowVersion.isBlank()) {
            stageQ.addCriteria(new Criteria().orOperator(
                    Criteria.where("workflowVersion").is(workflowVersion),
                    Criteria.where("workflowVersion").exists(false),
                    Criteria.where("workflowVersion").is(null)
            ));
        }
        Document stageDoc = mongoTemplate.findOne(stageQ, Document.class, STAGES_COLLECTION);
        if (stageDoc == null) {
            log.warn("No stage definition found for workflow={}, stage={}", workflowCode, currentStageCode);
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> allowedActionCodes = (List<String>) stageDoc.get("allowedActionCodes");
        if (allowedActionCodes == null || allowedActionCodes.isEmpty()) {
            return Collections.emptyList();
        }

        // Segregation of duties check: identify prior submitters / reviewers on this batch stage
        Set<String> priorActors = getPriorSubmittersAndReviewers(instance.getInstanceId(), batchNo, lotNo, equipmentCode);
        boolean isInitiatorOrReviewer = priorActors.contains(userId.toUpperCase(Locale.ROOT));

        List<AllowedActionDto> result = new ArrayList<>();

        for (String actionCode : allowedActionCodes) {
            // Check transition from this stage via actionCode
            Query transQ = new Query(Criteria.where("workflowCode").is(workflowCode)
                    .and("fromStageCode").is(currentStageCode)
                    .and("actionCode").is(actionCode));
            Document transDoc = mongoTemplate.findOne(transQ, Document.class, TRANSITIONS_COLLECTION);
            if (transDoc == null) continue;

            // Check action details
            Query actQ = new Query(Criteria.where("actionCode").is(actionCode));
            Document actDoc = mongoTemplate.findOne(actQ, Document.class, ACTIONS_COLLECTION);

            String applicableRole = actDoc != null ? actDoc.getString("applicableRole") : stageDoc.getString("assignedRole");
            if ("REQUEST_ADDITIONAL_INFO".equalsIgnoreCase(actionCode) || "REJECT".equalsIgnoreCase(actionCode)) {
                if ("REVIEW".equalsIgnoreCase(currentStageCode)) {
                    applicableRole = "PRODUCTION_REVIEWER";
                } else if ("APPROVAL".equalsIgnoreCase(currentStageCode)) {
                    applicableRole = "QA_APPROVER";
                }
            }
            if (applicableRole == null) applicableRole = "";
            applicableRole = applicableRole.toUpperCase(Locale.ROOT);

            // Validate user authorization for this action
            boolean isAuthorized = isUserAuthorizedForRole(userRoleCode, applicableRole);

            // Filter action applicability based on currentStatus within stage
            if ("SUBMISSION".equalsIgnoreCase(currentStageCode)) {
                if ("RETURNED_TO_OPERATOR".equalsIgnoreCase(currentStatus) || "ADDITIONAL_INFO_REQUESTED".equalsIgnoreCase(currentStatus)) {
                    if ("SUBMIT_FOR_REVIEW".equalsIgnoreCase(actionCode) || "SEND_FOR_REVIEW".equalsIgnoreCase(actionCode)) {
                        isAuthorized = false; // Must submit response upon return
                    }
                } else {
                    if ("SUBMIT_RESPONSE".equalsIgnoreCase(actionCode) || "SUBMIT_JUSTIFICATION".equalsIgnoreCase(actionCode)) {
                        isAuthorized = false; // Only for returned / additional-info requested records
                    }
                }
            }

            // Segregation of Duties: If stage is APPROVAL / action is APPROVE, submitter/reviewer cannot approve
            if ("APPROVAL".equalsIgnoreCase(currentStageCode) || "APPROVE".equalsIgnoreCase(actionCode)) {
                if (isInitiatorOrReviewer) {
                    isAuthorized = false; // Denied by segregation of duties
                }
            }

            if (!isAuthorized) {
                continue;
            }

            String actionName = actDoc != null ? actDoc.getString("actionName") : actionCode;
            String displayName = actDoc != null && actDoc.getString("displayName") != null ? actDoc.getString("displayName") : actionName;

            AllowedActionDto dto = AllowedActionDto.builder()
                    .actionCode(actionCode)
                    .actionName(actionName)
                    .displayName(displayName)
                    .actionType(actDoc != null ? actDoc.getString("actionType") : "TRANSITION")
                    .applicableRole(applicableRole)
                    .requiresEsign(actDoc != null ? actDoc.getBoolean("requiresEsign", true) : true)
                    .requiresComment(actDoc != null ? actDoc.getBoolean("requiresComment", false) : false)
                    .requiresJustification(actDoc != null ? actDoc.getBoolean("requiresJustification", false) : false)
                    .requiresAdditionalInfo(actDoc != null ? actDoc.getBoolean("requiresAdditionalInfo", false) : "REQUEST_ADDITIONAL_INFO".equalsIgnoreCase(actionCode))
                    .requiresResponse(actDoc != null ? actDoc.getBoolean("requiresResponse", false) : "SUBMIT_RESPONSE".equalsIgnoreCase(actionCode))
                    .requiresUserSelection(actDoc != null ? actDoc.getBoolean("requiresUserSelection", false) : false)
                    .requiresConfirmation(actDoc != null ? actDoc.getBoolean("requiresConfirmation", true) : true)
                    .fromStageCode(currentStageCode)
                    .toStageCode(transDoc.getString("toStageCode"))
                    .resultingStatus(transDoc.getString("resultingStatus"))
                    .returnStageCode(transDoc.getString("returnStageCode"))
                    .build();

            result.add(dto);
        }

        return deduplicateActions(result);
    }

    public static List<AllowedActionDto> deduplicateActions(List<AllowedActionDto> actions) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, AllowedActionDto> canonicalMap = new LinkedHashMap<>();

        for (AllowedActionDto action : actions) {
            String code = action.getActionCode() != null ? action.getActionCode().toUpperCase(Locale.ROOT) : "";
            String canonicalKey = resolveCanonicalActionKey(code, action.getFromStageCode(), action.getToStageCode(), action.getResultingStatus());

            if (canonicalMap.containsKey(canonicalKey)) {
                AllowedActionDto existing = canonicalMap.get(canonicalKey);
                if (isCanonicalActionCode(code) && !isCanonicalActionCode(existing.getActionCode())) {
                    canonicalMap.put(canonicalKey, action);
                }
            } else {
                canonicalMap.put(canonicalKey, action);
            }
        }

        return new ArrayList<>(canonicalMap.values());
    }

    private static boolean isCanonicalActionCode(String actionCode) {
        if (actionCode == null) return false;
        String upper = actionCode.toUpperCase(Locale.ROOT);
        return upper.equals("SUBMIT_FOR_REVIEW")
                || upper.equals("SUBMIT_FOR_APPROVAL")
                || upper.equals("SUBMIT_RESPONSE")
                || upper.equals("APPROVE")
                || upper.equals("DEFER")
                || upper.equals("REQUEST_ADDITIONAL_INFO");
    }

    private static String resolveCanonicalActionKey(String actionCode, String fromStage, String toStage, String resultingStatus) {
        String upper = actionCode != null ? actionCode.toUpperCase(Locale.ROOT) : "";
        if ("SEND_FOR_REVIEW".equals(upper)) return "SUBMIT_FOR_REVIEW:" + fromStage + ":" + toStage + ":" + resultingStatus;
        if ("SEND_FOR_APPROVAL".equals(upper)) return "SUBMIT_FOR_APPROVAL:" + fromStage + ":" + toStage + ":" + resultingStatus;
        if ("SUBMIT_JUSTIFICATION".equals(upper)) return "SUBMIT_RESPONSE:" + fromStage + ":" + toStage + ":" + resultingStatus;
        if ("REJECT".equals(upper)) return "REQUEST_ADDITIONAL_INFO:" + fromStage + ":" + toStage + ":" + resultingStatus;
        return upper + ":" + fromStage + ":" + toStage + ":" + resultingStatus;
    }

    // ============================================
    // E-SIGNATURE VERIFICATION
    // ============================================

    public boolean verifyEsignature(String userId, String rawPassword, String actionCode, String tenantId) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new UnauthorizedException("E-signature password is required.");
        }
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Authenticated user context is required for e-signature.");
        }

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("userId").regex("^" + userId.trim() + "$", "i"),
                Criteria.where("username").regex("^" + userId.trim() + "$", "i"),
                Criteria.where("email").regex("^" + userId.trim() + "$", "i")
        ));

        Document credential = mongoTemplate.findOne(query, Document.class, CREDENTIALS_COLLECTION);
        if (credential == null) {
            credential = mongoTemplate.findOne(query, Document.class, "auth_credentials");
        }
        if (credential == null) {
            query = new Query(new Criteria().orOperator(
                    Criteria.where("userId").regex("^" + userId.trim() + "$", "i"),
                    Criteria.where("username").regex("^" + userId.trim() + "$", "i")
            ));
            credential = mongoTemplate.findOne(query, Document.class, "auth_users");
        }

        if (credential == null) {
            log.warn("E-signature verification failed: No credential found for user '{}'", userId);
            emitFailedEsignAuditEvent(tenantId, userId, actionCode, "User credential record not found");
            throw new UnauthorizedException("E-signature validation failed: Invalid credentials.");
        }

        String passwordHash = credential.getString("passwordHash");
        if (passwordHash == null || passwordHash.isBlank()) {
            passwordHash = credential.getString("password");
        }

        if (passwordHash == null || !PasswordEncoderConfig.matches(rawPassword.trim(), passwordHash)) {
            log.warn("E-signature verification failed: Invalid password supplied for user '{}'", userId);
            emitFailedEsignAuditEvent(tenantId, userId, actionCode, "Invalid password verification");
            throw new UnauthorizedException("E-signature validation failed: Invalid password supplied.");
        }

        log.info("E-signature successfully verified for user '{}' on action '{}'", userId, actionCode);
        return true;
    }

    // ============================================
    // WORKFLOW ACTION EXECUTION
    // ============================================

    public Map<String, Object> executeAction(ActionExecutionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId().trim() : "SYSTEM";
        String userRole = request.getUserRole() != null && !request.getUserRole().isBlank()
                ? request.getUserRole().toUpperCase(Locale.ROOT).trim() : resolveUserRoleCode(userId);
        String tenantId = request.getTenantId();
        String plantId = request.getPlantId();
        String batchNo = request.getBatchNo();
        String lotNo = request.getLotNo();
        String equipmentCode = request.getEquipmentCode();
        String actionCode = request.getActionCode() != null ? request.getActionCode().toUpperCase(Locale.ROOT).trim() : "";
        String comments = request.getComments() != null ? request.getComments().trim() : "";
        String justification = request.getJustification() != null ? request.getJustification().trim() : "";
        String supervisorName = request.getSupervisorName() != null ? request.getSupervisorName().trim() : "";

        if (batchNo == null || batchNo.isBlank() || lotNo == null || lotNo.isBlank() || equipmentCode == null || equipmentCode.isBlank()) {
            throw new BusinessException("batchNo, lotNo, and equipmentCode are required.", "VALIDATION_ERROR");
        }
        if (actionCode.isBlank()) {
            throw new BusinessException("actionCode is required.", "VALIDATION_ERROR");
        }

        // Get allowed actions for this user on this batch stage
        List<AllowedActionDto> allowed = getAllowedActions(
                userId, userRole, tenantId, plantId, batchNo, lotNo, equipmentCode);

        AllowedActionDto targetAction = allowed.stream()
                .filter(a -> actionCode.equalsIgnoreCase(a.getActionCode()))
                .findFirst()
                .orElse(null);

        if (targetAction == null) {
            throw new ForbiddenException(
                    "User '" + userId + "' with role '" + userRole + "' is not permitted to execute action '" 
                            + actionCode + "' on batch=" + batchNo + " stage=" + equipmentCode);
        }

        // Mandatory justification or comment checks
        String additionalInfo = request.getAdditionalInformation() != null ? request.getAdditionalInformation().trim() : "";
        String responseNotes = request.getResponseNotes() != null ? request.getResponseNotes().trim() : "";

        if (Boolean.TRUE.equals(targetAction.getRequiresJustification()) || "DEFER".equalsIgnoreCase(actionCode)) {
            String combined = (justification + " " + comments + " " + additionalInfo + " " + responseNotes).trim();
            if (combined.isEmpty()) {
                throw new BusinessException(
                        "Action '" + actionCode + "' requires mandatory justification/reason. Please provide details.",
                        "JUSTIFICATION_REQUIRED");
            }
        }

        if (Boolean.TRUE.equals(targetAction.getRequiresAdditionalInfo()) || "REQUEST_ADDITIONAL_INFO".equalsIgnoreCase(actionCode)) {
            String combined = (additionalInfo + " " + comments + " " + justification).trim();
            if (combined.isEmpty()) {
                throw new BusinessException(
                        "Action 'Request Additional Information' requires specific additional information request notes.",
                        "ADDITIONAL_INFO_REQUIRED");
            }
        }

        if (Boolean.TRUE.equals(targetAction.getRequiresResponse()) || "SUBMIT_RESPONSE".equalsIgnoreCase(actionCode)) {
            String combined = (responseNotes + " " + comments + " " + justification).trim();
            if (combined.isEmpty()) {
                throw new BusinessException(
                        "Action 'Submit Response' requires response clarification notes.",
                        "RESPONSE_REQUIRED");
            }
        }

        // E-Signature validation
        if (Boolean.TRUE.equals(targetAction.getRequiresEsign())) {
            verifyEsignature(userId, request.getPassword(), actionCode, tenantId);
        }

        // Load Batch Summary
        Query query = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }
        query.addCriteria(Criteria.where("batchNo").is(batchNo));
        query.addCriteria(Criteria.where("lotNo").is(lotNo));

        Document summary = mongoTemplate.findOne(query, Document.class, BATCH_SUMMARY_COLLECTION);
        if (summary == null) {
            Query fallback = new Query(Criteria.where("batchNo").is(batchNo).and("lotNo").is(lotNo));
            summary = mongoTemplate.findOne(fallback, Document.class, BATCH_SUMMARY_COLLECTION);
        }
        if (summary == null) {
            throw new BusinessException("Batch summary not found for batch=" + batchNo + ", lot=" + lotNo);
        }

        @SuppressWarnings("unchecked")
        List<Document> stages = (List<Document>) summary.get("stages");
        if (stages == null || stages.isEmpty()) {
            throw new BusinessException("No stages configured in batch summary");
        }

        Document targetStage = null;
        for (Document stage : stages) {
            String sCode = stage.getString("equipmentCode");
            String sId = stage.getString("equipmentId");
            if (equipmentCode.equalsIgnoreCase(sCode != null ? sCode : "") 
                    || equipmentCode.equalsIgnoreCase(sId != null ? sId : "")) {
                targetStage = stage;
                break;
            }
        }
        if (targetStage == null) targetStage = stages.get(0);

        Document approval = targetStage.get("approval", Document.class);
        if (approval == null) approval = new Document();

        String previousStatus = approval.getString("status");
        if (previousStatus == null || previousStatus.isBlank()) previousStatus = "PENDING";

        // Concurrency / duplicate transition protection
        if ("APPROVED".equalsIgnoreCase(previousStatus) || "COMPLETED".equalsIgnoreCase(previousStatus)) {
            throw new BusinessException("Stage " + equipmentCode + " for batch " + batchNo + " has already been approved.", "CONCURRENT_MODIFICATION");
        }

        String targetStatus = targetAction.getResultingStatus();
        String nextStageCode = targetAction.getToStageCode();

        // Handle Return Routing for Provide Additional Information (Stage 2A to Reviewer vs Stage 3A to QA Approver)
        if ("SUBMIT_RESPONSE".equalsIgnoreCase(actionCode) || "SUBMIT_JUSTIFICATION".equalsIgnoreCase(actionCode) || "PROVIDE_ADDITIONAL_INFO".equalsIgnoreCase(actionCode)) {
            String returnedFrom = approval.getString("returnedFromStage");
            if ("APPROVAL".equalsIgnoreCase(returnedFrom)) {
                nextStageCode = "APPROVAL";
                targetStatus = "PENDING_APPROVAL";
            } else {
                nextStageCode = "REVIEW";
                targetStatus = "UNDER_REVIEW";
            }
        }

        Date now = Date.from(Instant.now());

        String effectiveComment = !comments.isEmpty() ? comments : (!justification.isEmpty() ? justification : (!additionalInfo.isEmpty() ? additionalInfo : responseNotes));

        // Update stage approval sub-document
        approval.put("status", targetStatus);
        approval.put("previousStatus", previousStatus);
        approval.put("comments", effectiveComment);
        approval.put("justification", !justification.isEmpty() ? justification : effectiveComment);
        approval.put("transitionedBy", userId);
        approval.put("transitionedAt", now);

        if ("APPROVED".equalsIgnoreCase(targetStatus)) {
            approval.put("approvedBy", userId);
            approval.put("approvedAt", now);
            try {
                BatchPdfGeneratorService.PdfGenerationResult pdfRes = batchPdfGeneratorService.generateAndStoreBatchPdf(
                        batchNo, lotNo, equipmentCode, tenantId, plantId);
                approval.put("pdfDocumentId", pdfRes.getDocumentId());
                approval.put("pdfStoragePath", pdfRes.getStoragePath());
                approval.put("pdfSha256Checksum", pdfRes.getSha256Checksum());
                approval.put("pdfGeneratedAt", now);
                approval.put("pdfStatus", "READY");

                summary.put("pdfDocumentId", pdfRes.getDocumentId());
                summary.put("pdfStoragePath", pdfRes.getStoragePath());
                summary.put("pdfSha256Checksum", pdfRes.getSha256Checksum());
            } catch (Exception ex) {
                log.error("Failed to automatically generate PDF on approval for batch={}: {}", batchNo, ex.getMessage(), ex);
                approval.put("pdfStatus", "FAILED");
            }
        } else if ("DEFERRED".equalsIgnoreCase(targetStatus)) {
            approval.put("deferredBy", userId);
            approval.put("deferredAt", now);
            approval.put("deferralReason", effectiveComment);
        } else if ("UNDER_REVIEW".equalsIgnoreCase(targetStatus)) {
            approval.put("assignedRole", "PRODUCTION_REVIEWER");
            if (!supervisorName.isEmpty()) {
                approval.put("assignedTo", supervisorName);
            } else {
                approval.remove("assignedTo");
            }
            if ("SEND_FOR_REVIEW".equalsIgnoreCase(actionCode) || "SUBMIT_FOR_REVIEW".equalsIgnoreCase(actionCode)) {
                approval.put("requestedBy", userId);
                approval.put("requestedAt", now);
                targetStage.put("requestedBy", userId);
                targetStage.put("requestedAt", now);
            }
            if ("SUBMIT_RESPONSE".equalsIgnoreCase(actionCode) || "SUBMIT_JUSTIFICATION".equalsIgnoreCase(actionCode) || "PROVIDE_ADDITIONAL_INFO".equalsIgnoreCase(actionCode)) {
                approval.put("responseProvidedBy", userId);
                approval.put("responseProvidedAt", now);
                approval.put("responseNotes", !responseNotes.isEmpty() ? responseNotes : effectiveComment);
            }
            if (!supervisorName.isEmpty()) {
                targetStage.put("supervisorName", supervisorName);
            }
        } else if ("REVIEWER_REVIEWED".equalsIgnoreCase(targetStatus) || "PENDING_APPROVAL".equalsIgnoreCase(targetStatus)) {
            approval.put("assignedRole", "QA_APPROVER");
            approval.put("reviewedBy", userId);
            approval.put("reviewedAt", now);
            if (!supervisorName.isEmpty()) {
                approval.put("assignedTo", supervisorName);
            } else {
                approval.remove("assignedTo");
            }
            if ("SUBMIT_RESPONSE".equalsIgnoreCase(actionCode) || "SUBMIT_JUSTIFICATION".equalsIgnoreCase(actionCode) || "PROVIDE_ADDITIONAL_INFO".equalsIgnoreCase(actionCode)) {
                approval.put("responseProvidedBy", userId);
                approval.put("responseProvidedAt", now);
                approval.put("responseNotes", !responseNotes.isEmpty() ? responseNotes : effectiveComment);
            }
            if (!supervisorName.isEmpty()) {
                targetStage.put("supervisorName", supervisorName);
            }
        } else if ("RETURNED_TO_OPERATOR".equalsIgnoreCase(targetStatus) || "REJECTED".equalsIgnoreCase(targetStatus) || "ADDITIONAL_INFO_REQUESTED".equalsIgnoreCase(targetStatus)) {
            approval.put("assignedRole", "PRODUCTION_OPERATOR");
            approval.remove("assignedTo");
            approval.remove("activeReviewer");
            approval.put("returnedFromStage", targetAction.getFromStageCode());
            approval.put("rejectedBy", userId);
            approval.put("rejectedAt", now);
            approval.put("additionalInfoRequestedBy", userId);
            approval.put("additionalInfoRequestedAt", now);
            approval.put("additionalInformation", !additionalInfo.isEmpty() ? additionalInfo : effectiveComment);
            approval.put("rejectionReason", !additionalInfo.isEmpty() ? additionalInfo : effectiveComment);
        }

        targetStage.put("approval", approval);

        // Derive overall status
        summary.put("overallStatus", deriveBatchOverallStatus(stages));
        summary.put("updatedAt", now);

        // Save Batch Summary
        Document savedSummary = mongoTemplate.save(summary, BATCH_SUMMARY_COLLECTION);

        // Update or create runtime WorkflowInstance
        WorkflowInstance instance = getOrCreateWorkflowInstance(
                batchNo, lotNo, equipmentCode, tenantId, plantId, previousStatus, userId);
        if (instance != null) {
            instance.setCurrentStageCode(nextStageCode);
            instance.setCurrentStatus(targetStatus);
            instance.setLastActionCode(actionCode);
            instance.setLastActionBy(userId);
            instance.setLastActionAt(now.toInstant());
            if ("RETURNED_TO_OPERATOR".equalsIgnoreCase(targetStatus) || "REJECTED".equalsIgnoreCase(targetStatus)) {
                instance.setAssignedTo(null);
            } else {
                instance.setAssignedTo(!supervisorName.isEmpty() ? supervisorName : null);
            }
            instance.setIsTerminal("APPROVED".equalsIgnoreCase(targetStatus));
            instance.setUpdatedAt(now.toInstant());
            mongoTemplate.save(instance, INSTANCE_COLLECTION);
        }

        // Record Workflow Action History
        WorkflowActionHistory history = WorkflowActionHistory.builder()
                .historyId("HIST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .instanceId(instance != null ? instance.getInstanceId() : "")
                .workflowCode(instance != null ? instance.getWorkflowCode() : "IIOT_BATCH_STAGE_WORKFLOW")
                .workflowVersion(instance != null ? instance.getWorkflowVersion() : "1.0.0")
                .entityId(batchNo + ":" + lotNo + ":" + equipmentCode)
                .batchNo(batchNo)
                .lotNo(lotNo)
                .equipmentCode(equipmentCode)
                .fromStageCode(targetAction.getFromStageCode())
                .toStageCode(nextStageCode)
                .actionCode(actionCode)
                .actionName(targetAction.getActionName())
                .previousStatus(previousStatus)
                .newStatus(targetStatus)
                .performedBy(userId)
                .performerName(userId)
                .performerRole(userRole)
                .comments(effectiveComment)
                .justification(justification)
                .esignatureVerified(true)
                .esignatureReason(request.getEsignatureReason() != null ? request.getEsignatureReason() : "Workflow Transition Approval")
                .tenantId(tenantId)
                .plantId(plantId != null ? plantId : summary.getString("plantId"))
                .timestamp(now.toInstant())
                .build();

        mongoTemplate.save(history, HISTORY_COLLECTION);

        // Emit Immutable 21 CFR Part 11 Audit Trail Event
        emitWorkflowAuditEvent(tenantId, batchNo, lotNo, equipmentCode,
                previousStatus, targetStatus, actionCode, userId, userRole,
                effectiveComment, now);

        // Emit notifications
        String effectivePlantId = summary.getString("plantId");
        notificationService.emitWorkflowTransitionNotification(
                tenantId, effectivePlantId, batchNo, lotNo, equipmentCode,
                previousStatus, targetStatus, userId, userRole, effectiveComment, targetStage);

        if (!supervisorName.isEmpty()) {
            notificationService.emitAssignmentNotification(
                    tenantId, effectivePlantId, batchNo, lotNo, equipmentCode, supervisorName, userId);
        }

        log.info("Workflow execution success: batch={} stage={} action={} [{}] → [{}] by user={} role={}",
                batchNo, equipmentCode, actionCode, previousStatus, targetStatus, userId, userRole);

        return toMap(savedSummary);
    }

    // ============================================
    // BULK WORKFLOW ACTION EXECUTION
    // ============================================

    public BulkExecutionResult executeBulkAction(BulkActionExecutionRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("No batch tasks provided for bulk execution", "EMPTY_BULK_REQUEST");
        }

        String userId = request.getUserId() != null ? request.getUserId().trim() : "SYSTEM";
        String userRole = request.getUserRole() != null && !request.getUserRole().isBlank()
                ? request.getUserRole().toUpperCase(Locale.ROOT).trim() : resolveUserRoleCode(userId);

        if (ROLE_ADMIN.equalsIgnoreCase(userRole) || "SUPER_ADMIN".equalsIgnoreCase(userRole)) {
            throw new ForbiddenException(
                    "User '" + userId + "' with role '" + userRole + "' is not permitted to execute operational workflow actions. Administrative access is restricted to Master Management.",
                    "FORBIDDEN");
        }

        // Verify e-signature upfront if action requires it
        Query actQ = new Query(Criteria.where("actionCode").is(request.getActionCode()));
        Document actDoc = mongoTemplate.findOne(actQ, Document.class, ACTIONS_COLLECTION);
        boolean requiresEsign = actDoc != null ? actDoc.getBoolean("requiresEsign", true) : true;
        if (requiresEsign) {
            verifyEsignature(userId, request.getPassword(), request.getActionCode(), request.getTenantId());
        }

        List<BulkSuccessItem> successList = new ArrayList<>();
        List<BulkFailureItem> failureList = new ArrayList<>();

        for (BulkActionItem item : request.getItems()) {
            if (item == null || item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                continue;
            }

            ActionExecutionRequest singleReq = ActionExecutionRequest.builder()
                    .userId(userId)
                    .userRole(userRole)
                    .tenantId(request.getTenantId())
                    .plantId(request.getPlantId())
                    .batchNo(item.getBatchNo())
                    .lotNo(item.getLotNo())
                    .equipmentCode(item.getEquipmentCode())
                    .actionCode(request.getActionCode())
                    .password(request.getPassword())
                    .comments(request.getComments())
                    .justification(request.getJustification())
                    .additionalInformation(request.getAdditionalInformation())
                    .responseNotes(request.getResponseNotes())
                    .supervisorName(request.getSupervisorName())
                    .esignatureReason(request.getEsignatureReason())
                    .build();

            try {
                Map<String, Object> execResult = executeAction(singleReq);
                String newStatus = "UPDATED";
                if (execResult != null && execResult.containsKey("overallStatus")) {
                    newStatus = String.valueOf(execResult.get("overallStatus"));
                }
                successList.add(BulkSuccessItem.builder()
                        .batchNo(item.getBatchNo())
                        .lotNo(item.getLotNo() != null ? item.getLotNo() : "")
                        .equipmentCode(item.getEquipmentCode() != null ? item.getEquipmentCode() : "")
                        .newStatus(newStatus)
                        .message("Action " + request.getActionCode() + " executed successfully.")
                        .build());
            } catch (BusinessException | ForbiddenException | UnauthorizedException ex) {
                log.warn("Bulk item execution failed for batch={}, lot={}, equipment={}: {}",
                        item.getBatchNo(), item.getLotNo(), item.getEquipmentCode(), ex.getMessage());
                String code = "EXECUTION_ERROR";
                if (ex instanceof BusinessException be && be.getErrorCode() != null) code = be.getErrorCode();
                if (ex instanceof ForbiddenException fe && fe.getErrorCode() != null) code = fe.getErrorCode();
                if (ex instanceof UnauthorizedException ue && ue.getErrorCode() != null) code = ue.getErrorCode();

                failureList.add(BulkFailureItem.builder()
                        .batchNo(item.getBatchNo())
                        .lotNo(item.getLotNo() != null ? item.getLotNo() : "")
                        .equipmentCode(item.getEquipmentCode() != null ? item.getEquipmentCode() : "")
                        .reason(ex.getMessage())
                        .errorCode(code)
                        .build());
            } catch (Exception ex) {
                log.error("Unexpected error during bulk item execution for batch={}, lot={}, equipment={}",
                        item.getBatchNo(), item.getLotNo(), item.getEquipmentCode(), ex);
                failureList.add(BulkFailureItem.builder()
                        .batchNo(item.getBatchNo())
                        .lotNo(item.getLotNo() != null ? item.getLotNo() : "")
                        .equipmentCode(item.getEquipmentCode() != null ? item.getEquipmentCode() : "")
                        .reason("Internal execution error: " + ex.getMessage())
                        .errorCode("INTERNAL_ERROR")
                        .build());
            }
        }

        return BulkExecutionResult.builder()
                .totalRequested(request.getItems().size())
                .successCount(successList.size())
                .failureCount(failureList.size())
                .successfulItems(successList)
                .failedItems(failureList)
                .build();
    }

    // ============================================
    // DASHBOARD COUNTS
    // ============================================

    public Map<String, Object> getDashboardCounts(String userId, String userRole, String tenantId, String plantId) {
        userId = userId != null ? userId.trim() : "SYSTEM";
        userRole = userRole != null && !userRole.isBlank() ? userRole.toUpperCase(Locale.ROOT).trim() : resolveUserRoleCode(userId);

        Query query = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        List<Document> summaries = mongoTemplate.find(query, Document.class, BATCH_SUMMARY_COLLECTION);

        int pendingMyAction = 0;
        int pendingReview = 0;
        int pendingApproval = 0;
        int completedActions = 0;

        for (Document summary : summaries) {
            String batchNo = summary.getString("batchNo");
            String lotNo = summary.getString("lotNo");
            @SuppressWarnings("unchecked")
            List<Document> stages = (List<Document>) summary.get("stages");
            if (stages == null) continue;

            for (Document stage : stages) {
                String equipmentCode = stage.getString("equipmentCode");
                Document approval = stage.get("approval", Document.class);
                String status = approval != null && approval.getString("status") != null 
                        ? approval.getString("status").toUpperCase(Locale.ROOT) : "PENDING";

                if ("UNDER_REVIEW".equals(status) || "IN_REVIEW".equals(status)) {
                    pendingReview++;
                } else if ("REVIEWER_REVIEWED".equals(status) || "PENDING_APPROVAL".equals(status)) {
                    pendingApproval++;
                } else if ("APPROVED".equals(status) || "REJECTED".equals(status) || "DEFERRED".equals(status)) {
                    completedActions++;
                }

                // Check if actionable for this user
                List<AllowedActionDto> allowed = getAllowedActions(
                        userId, userRole, tenantId, plantId, batchNo, lotNo, equipmentCode);
                if (!allowed.isEmpty()) {
                    pendingMyAction++;
                }
            }
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("pendingMyAction", pendingMyAction);
        counts.put("pendingReview", pendingReview);
        counts.put("pendingApproval", pendingApproval);
        counts.put("completedActions", completedActions);
        counts.put("userRole", userRole);
        counts.put("userId", userId);
        return counts;
    }

    // ============================================
    // WORKFLOW AUDIT TRAIL & HISTORY
    // ============================================

    public List<Map<String, Object>> getWorkflowAuditTrail(String batchNo, String lotNo, String equipmentCode, String tenantId) {
        Query query = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(Criteria.where("tenantId").is(tenantId));
        }
        if (batchNo != null && !batchNo.isBlank()) {
            query.addCriteria(Criteria.where("batchNo").is(batchNo));
        }
        if (lotNo != null && !lotNo.isBlank()) {
            query.addCriteria(Criteria.where("lotNo").is(lotNo));
        }
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            query.addCriteria(Criteria.where("equipmentCode").is(equipmentCode));
        }
        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        query.limit(100);

        List<Document> docs = mongoTemplate.find(query, Document.class, AUDIT_COLLECTION);
        return docs.stream().map(this::toMap).toList();
    }

    public List<WorkflowActionHistory> getWorkflowActionHistory(String batchNo, String lotNo, String equipmentCode) {
        Query query = new Query();
        if (batchNo != null && !batchNo.isBlank()) query.addCriteria(Criteria.where("batchNo").is(batchNo));
        if (lotNo != null && !lotNo.isBlank()) query.addCriteria(Criteria.where("lotNo").is(lotNo));
        if (equipmentCode != null && !equipmentCode.isBlank()) query.addCriteria(Criteria.where("equipmentCode").is(equipmentCode));
        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, WorkflowActionHistory.class, HISTORY_COLLECTION);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    public WorkflowInstance getOrCreateWorkflowInstance(
            String batchNo, String lotNo, String equipmentCode,
            String tenantId, String plantId, String initialStatus, String operatorId) {

        String entityId = batchNo + ":" + lotNo + ":" + equipmentCode;
        Query q = new Query(Criteria.where("entityId").is(entityId));
        WorkflowInstance instance = mongoTemplate.findOne(q, WorkflowInstance.class, INSTANCE_COLLECTION);

        if (instance != null) {
            return instance;
        }

        // Resolve active workflow definition
        Document def = resolveActiveWorkflowDefinition(MODULE_IIOT, ENTITY_BATCH_STAGE, tenantId, plantId);
        String workflowCode = def != null ? def.getString("workflowCode") : "IIOT_BATCH_STAGE_WORKFLOW";
        String workflowVersion = def != null ? def.getString("version") : "1.0.0";

        // Determine initial stage and status
        String status = initialStatus != null && !initialStatus.isBlank() ? initialStatus.toUpperCase(Locale.ROOT) : null;
        if (status == null) {
            try {
                Query bq = new Query(Criteria.where("batchNo").is(batchNo).and("lotNo").is(lotNo));
                Document summary = mongoTemplate.findOne(bq, Document.class, BATCH_SUMMARY_COLLECTION);
                if (summary != null) {
                    @SuppressWarnings("unchecked")
                    List<Document> stages = (List<Document>) summary.get("stages");
                    if (stages != null) {
                        for (Document stage : stages) {
                            String sc = stage.getString("equipmentCode");
                            String sid = stage.getString("equipmentId");
                            if (equipmentCode.equalsIgnoreCase(sc) || equipmentCode.equalsIgnoreCase(sid)) {
                                Document app = stage.get("approval", Document.class);
                                if (app != null && app.getString("status") != null && !app.getString("status").isBlank()) {
                                    status = app.getString("status").toUpperCase(Locale.ROOT);
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }

        String stageCode = "SUBMISSION";
        if ("UNDER_REVIEW".equals(status) || "IN_REVIEW".equals(status)) {
            stageCode = "REVIEW";
        } else if ("REVIEWER_REVIEWED".equals(status) || "PENDING_APPROVAL".equals(status) || "DEFERRED".equals(status)) {
            stageCode = "APPROVAL";
        } else if ("RETURNED_TO_OPERATOR".equals(status)) {
            stageCode = "SUBMISSION";
        } else if ("APPROVED".equals(status) || "REJECTED".equals(status)) {
            stageCode = "COMPLETED";
        }

        Instant now = Instant.now();
        instance = WorkflowInstance.builder()
                .instanceId("WFI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .workflowCode(workflowCode)
                .workflowVersion(workflowVersion)
                .entityType(ENTITY_BATCH_STAGE)
                .entityId(entityId)
                .batchNo(batchNo)
                .lotNo(lotNo)
                .equipmentCode(equipmentCode)
                .tenantId(tenantId)
                .plantId(plantId)
                .currentStageCode(stageCode)
                .currentStatus(status)
                .initiatedBy(operatorId != null ? operatorId : "SYSTEM")
                .initiatedAt(now)
                .isTerminal("APPROVED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status))
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            return mongoTemplate.save(instance, INSTANCE_COLLECTION);
        } catch (Exception e) {
            log.warn("Failed to create workflow instance for {}: {}", entityId, e.getMessage());
            return instance;
        }
    }

    private Document resolveActiveWorkflowDefinition(String module, String entity, String tenantId, String plantId) {
        Query query = new Query(Criteria.where("module").is(module)
                .and("entity").is(entity)
                .and("status").is("ACTIVE"));
        if (tenantId != null && !tenantId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        Document def = mongoTemplate.findOne(query, Document.class, DEFINITIONS_COLLECTION);
        if (def == null) {
            // Fallback to any active definition for module and entity
            def = mongoTemplate.findOne(new Query(Criteria.where("module").is(module).and("status").is("ACTIVE")),
                    Document.class, DEFINITIONS_COLLECTION);
        }
        return def;
    }

    private Set<String> getPriorSubmittersAndReviewers(String instanceId, String batchNo, String lotNo, String equipmentCode) {
        Set<String> actors = new HashSet<>();
        Query query = new Query();
        if (instanceId != null && !instanceId.isBlank()) {
            query.addCriteria(Criteria.where("instanceId").is(instanceId));
        } else {
            query.addCriteria(Criteria.where("batchNo").is(batchNo)
                    .and("lotNo").is(lotNo)
                    .and("equipmentCode").is(equipmentCode));
        }

        List<WorkflowActionHistory> list = mongoTemplate.find(query, WorkflowActionHistory.class, HISTORY_COLLECTION);
        for (WorkflowActionHistory h : list) {
            String fromStage = h.getFromStageCode() != null ? h.getFromStageCode().toUpperCase(Locale.ROOT) : "";
            String role = h.getPerformerRole() != null ? h.getPerformerRole().toUpperCase(Locale.ROOT) : "";
            // Only consider actors who submitted as operator or reviewed as reviewer (excluding QA Approvers)
            if ("SUBMISSION".equals(fromStage) || "REVIEW".equals(fromStage) || role.contains("OPERATOR") || role.contains("REVIEWER")) {
                if (h.getPerformedBy() != null && !h.getPerformedBy().isBlank() && !role.contains("APPROVER")) {
                    actors.add(h.getPerformedBy().toUpperCase(Locale.ROOT));
                }
            }
        }
        return actors;
    }

    public boolean isUserAuthorizedForRole(String userRoleCode, String applicableRole) {
        if (userRoleCode == null || userRoleCode.isBlank()) return false;
        String user = userRoleCode.toUpperCase(Locale.ROOT).trim();
        String required = applicableRole != null ? applicableRole.toUpperCase(Locale.ROOT).trim() : "";

        // Administrative roles (SUPER_ADMIN, PLATFORM_ADMIN, IT_ADMIN, ADMIN) do not have implicit operational mutation authority
        boolean isAdmin = user.contains("SUPER_ADMIN") || user.contains("PLATFORM_ADMIN") || user.equals("IT_ADMIN") || user.equals("ADMIN");
        boolean isOperational = user.contains("OPERATOR") || user.contains("REVIEWER") || user.contains("APPROVER") || user.contains("SUPERVISOR");

        if (isAdmin && !isOperational) {
            // Administrative roles can only execute actions that explicitly specify their role
            if (!required.isEmpty() && !"*".equals(required) && (required.equalsIgnoreCase(user) || required.equalsIgnoreCase("SUPER_ADMIN") || required.equalsIgnoreCase("PLATFORM_SUPER_ADMIN"))) {
                return true;
            }
            return false;
        }

        if (required.isEmpty() || "*".equals(required)) {
            return true;
        }

        if (required.contains("OPERATOR") && user.contains("OPERATOR")) return true;
        if (required.contains("REVIEWER") && (user.contains("REVIEWER") || user.contains("QA"))) return true;
        if (required.contains("APPROVER") && (user.contains("APPROVER") || user.contains("SUPERVISOR") || user.contains("QA"))) return true;
        if (required.contains("SUPERVISOR") && (user.contains("SUPERVISOR") || user.contains("APPROVER"))) return true;

        return user.equalsIgnoreCase(required);
    }

    public String resolveUserRoleCode(String userId) {
        try {
            Query groupQuery = new Query(Criteria.where("userId").regex("^" + userId + "$", "i")
                    .and("isActive").is(true));
            List<Document> assignments = mongoTemplate.find(groupQuery, Document.class,
                    "mdm_user_assignments_to_user_groups");

            for (Document assignment : assignments) {
                String groupId = assignment.getString("groupId");
                if (groupId == null) continue;

                Query grpQuery = new Query(Criteria.where("groupId").is(groupId));
                Document group = mongoTemplate.findOne(grpQuery, Document.class, "mdm_user_groups");
                if (group != null) {
                    String groupCode = group.getString("groupCode");
                    if (groupCode != null) {
                        String code = groupCode.toUpperCase(Locale.ROOT);
                        if (code.contains("SUPER_ADMIN") || code.contains("PLATFORM_ADMIN")) return ROLE_ADMIN;
                        if (code.contains("APPROVER") || code.contains("SUPERVISOR")) return ROLE_APPROVER;
                        if (code.contains("REVIEWER") || code.contains("QA")) return ROLE_REVIEWER;
                        if (code.contains("OPERATOR")) return ROLE_OPERATOR;
                    }
                }

                Query roleAssignQ = new Query(Criteria.where("groupId").is(groupId).and("isActive").is(true));
                List<Document> roleAssignments = mongoTemplate.find(roleAssignQ, Document.class, "mdm_role_assignments_to_user_groups");
                for (Document roleAssign : roleAssignments) {
                    String roleId = roleAssign.getString("roleId");
                    if (roleId == null) continue;
                    Document roleDoc = mongoTemplate.findOne(new Query(Criteria.where("roleId").is(roleId)), Document.class, "mdm_roles");
                    if (roleDoc != null) {
                        String roleCode = roleDoc.getString("roleCode");
                        if (roleCode != null) {
                            String r = roleCode.toUpperCase(Locale.ROOT);
                            if (r.contains("SUPER_ADMIN") || r.contains("PLATFORM_ADMIN")) return ROLE_ADMIN;
                            if (r.contains("APPROVER") || r.contains("SUPERVISOR")) return ROLE_APPROVER;
                            if (r.contains("REVIEWER") || r.contains("QA")) return ROLE_REVIEWER;
                            if (r.contains("OPERATOR")) return ROLE_OPERATOR;
                        }
                    }
                }
            }
            return "";
        } catch (Exception e) {
            log.error("Failed to resolve role for user {}: {}", userId, e.getMessage());
            return "";
        }
    }

    private String deriveBatchOverallStatus(List<Document> stages) {
        boolean hasUnderReview = false;
        boolean hasReviewed = false;
        boolean allApproved = true;

        for (Document stage : stages) {
            String executionStatus = "";
            Object execObj = stage.get("executionStatus");
            if (execObj != null) executionStatus = execObj.toString().toUpperCase(Locale.ROOT);

            Document approval = stage.get("approval", Document.class);
            String approvalStatus = "PENDING";
            if (approval != null) {
                String s = approval.getString("status");
                if (s != null && !s.isBlank()) approvalStatus = s.toUpperCase(Locale.ROOT);
            }

            if ("DEFERRED".equals(approvalStatus)) {
                return "DEFERRED";
            }
            if ("REJECTED".equals(approvalStatus)) {
                return "REJECTED";
            }
            if ("RETURNED_TO_OPERATOR".equals(approvalStatus)) {
                return "RETURNED_TO_OPERATOR";
            }
            if ("UNDER_REVIEW".equals(approvalStatus) || "IN_REVIEW".equals(approvalStatus)) {
                hasUnderReview = true;
            }
            if ("REVIEWER_REVIEWED".equals(approvalStatus) || "PENDING_APPROVAL".equals(approvalStatus)) {
                hasReviewed = true;
            }
            if (!"NOT_STARTED".equals(executionStatus)
                    && !"APPROVED".equals(approvalStatus)
                    && !"RELEASED".equals(approvalStatus)) {
                allApproved = false;
            }
        }

        if (allApproved) return "APPROVED";
        if (hasReviewed) return "REVIEWER_REVIEWED";
        if (hasUnderReview) return "UNDER_REVIEW";
        return "IN_PROGRESS";
    }

    private void emitWorkflowAuditEvent(
            String tenantId, String batchNo, String lotNo,
            String equipmentCode, String previousStatus,
            String newStatus, String action, String userId,
            String userRole, String comments, Date timestamp) {

        Document auditEvent = new Document();
        auditEvent.put("tenantId", tenantId);
        auditEvent.put("batchNo", batchNo);
        auditEvent.put("lotNo", lotNo);
        auditEvent.put("equipmentCode", equipmentCode);
        auditEvent.put("previousStatus", previousStatus);
        auditEvent.put("newStatus", newStatus);
        auditEvent.put("action", action != null ? action : (previousStatus + "_TO_" + newStatus));
        auditEvent.put("userId", userId);
        auditEvent.put("userRole", userRole);
        auditEvent.put("comments", comments);
        auditEvent.put("timestamp", timestamp);
        auditEvent.put("createdAt", timestamp);
        auditEvent.put("esignatureVerified", true);
        auditEvent.put("regulatoryStatement", "Electronic Signature executed in compliance with 21 CFR Part 11 / EU Annex 11.");

        try {
            mongoTemplate.insert(auditEvent, AUDIT_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to persist workflow audit event for batch={} stage={}: {}",
                    batchNo, equipmentCode, e.getMessage());
        }
    }

    private void emitFailedEsignAuditEvent(String tenantId, String userId, String actionCode, String reason) {
        Document auditEvent = new Document();
        auditEvent.put("tenantId", tenantId);
        auditEvent.put("action", "FAILED_ESIGN_ATTEMPT");
        auditEvent.put("actionCode", actionCode);
        auditEvent.put("userId", userId);
        auditEvent.put("reason", reason);
        auditEvent.put("esignatureVerified", false);
        Date now = Date.from(Instant.now());
        auditEvent.put("timestamp", now);
        auditEvent.put("createdAt", now);

        try {
            mongoTemplate.insert(auditEvent, AUDIT_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to persist failed esign audit event: {}", e.getMessage());
        }
    }

    public Map<String, Object> claimWorkflowTask(String batchNo, String lotNo, String equipmentCode,
                                                 String userId, String userRole, String tenantId, String plantId) {
        WorkflowInstance instance = getOrCreateWorkflowInstance(batchNo, lotNo, equipmentCode, tenantId, plantId, null, userId);

        Instant now = Instant.now();
        instance.setAssignedTo(userId);
        instance.setUpdatedAt(now);

        Map<String, Object> ctx = instance.getContext() != null ? new LinkedHashMap<>(instance.getContext()) : new LinkedHashMap<>();
        ctx.put("activeReviewer", userId);
        ctx.put("activeReviewerRole", userRole);
        ctx.put("claimedAt", now.toString());
        instance.setContext(ctx);

        mongoTemplate.save(instance, INSTANCE_COLLECTION);

        try {
            Query q = new Query(Criteria.where("batchNo").is(batchNo));
            Document summary = mongoTemplate.findOne(q, Document.class, BATCH_SUMMARY_COLLECTION);
            if (summary != null) {
                @SuppressWarnings("unchecked")
                List<Document> stages = (List<Document>) summary.get("stages");
                if (stages != null) {
                    for (Document stage : stages) {
                        String eqC = stage.getString("equipmentCode");
                        String eqI = stage.getString("equipmentId");
                        String sid = (eqC != null && !eqC.isBlank()) ? eqC : eqI;
                        if (equipmentCode.equalsIgnoreCase(sid)) {
                            Document approval = stage.get("approval", Document.class);
                            if (approval == null) {
                                approval = new Document();
                                stage.put("approval", approval);
                            }
                            approval.put("assignedTo", userId);
                            approval.put("activeReviewer", userId);
                            approval.put("activeReviewerRole", userRole);
                            approval.put("claimedAt", Date.from(now));
                            break;
                        }
                    }
                    mongoTemplate.save(summary, BATCH_SUMMARY_COLLECTION);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not update batch summary for task claim: {}", ex.getMessage());
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "Task claimed successfully by " + userId);
        res.put("assignedTo", userId);
        res.put("activeReviewer", userId);
        res.put("activeReviewerRole", userRole);
        res.put("claimedAt", now.toString());
        return res;
    }

    public Map<String, Object> unclaimWorkflowTask(String batchNo, String lotNo, String equipmentCode,
                                                   String userId, String tenantId) {
        WorkflowInstance instance = getOrCreateWorkflowInstance(batchNo, lotNo, equipmentCode, tenantId, null, null, userId);

        Instant now = Instant.now();
        instance.setAssignedTo(null);
        instance.setUpdatedAt(now);

        Map<String, Object> ctx = instance.getContext() != null ? new LinkedHashMap<>(instance.getContext()) : new LinkedHashMap<>();
        ctx.remove("activeReviewer");
        ctx.remove("activeReviewerRole");
        ctx.remove("claimedAt");
        instance.setContext(ctx);

        mongoTemplate.save(instance, INSTANCE_COLLECTION);

        try {
            Query q = new Query(Criteria.where("batchNo").is(batchNo));
            Document summary = mongoTemplate.findOne(q, Document.class, BATCH_SUMMARY_COLLECTION);
            if (summary != null) {
                @SuppressWarnings("unchecked")
                List<Document> stages = (List<Document>) summary.get("stages");
                if (stages != null) {
                    for (Document stage : stages) {
                        String eqC = stage.getString("equipmentCode");
                        String eqI = stage.getString("equipmentId");
                        String sid = (eqC != null && !eqC.isBlank()) ? eqC : eqI;
                        if (equipmentCode.equalsIgnoreCase(sid)) {
                            Document approval = stage.get("approval", Document.class);
                            if (approval != null) {
                                approval.remove("assignedTo");
                                approval.remove("activeReviewer");
                                approval.remove("activeReviewerRole");
                                approval.remove("claimedAt");
                            }
                            break;
                        }
                    }
                    mongoTemplate.save(summary, BATCH_SUMMARY_COLLECTION);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not update batch summary for task unclaim: {}", ex.getMessage());
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "Task released successfully by " + userId);
        return res;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Document doc) {
        if (doc == null) return Collections.emptyMap();
        Map<String, Object> map = new LinkedHashMap<>(doc);
        map.remove("_class");
        Object id = map.get("_id");
        if (id != null) map.put("_id", id.toString());

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Document) {
                entry.setValue(toMap((Document) entry.getValue()));
            } else if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                List<Object> converted = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Document) {
                        converted.add(toMap((Document) item));
                    } else {
                        converted.add(item);
                    }
                }
                entry.setValue(converted);
            }
        }
        return map;
    }
}
