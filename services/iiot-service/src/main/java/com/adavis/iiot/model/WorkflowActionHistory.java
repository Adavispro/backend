package com.adavis.iiot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "iiot_workflow_action_history")
public class WorkflowActionHistory {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String historyId;

    @Indexed
    private String instanceId;

    private String workflowCode;
    private String workflowVersion;

    @Indexed
    private String entityId;

    @Indexed
    private String batchNo;

    @Indexed
    private String lotNo;

    @Indexed
    private String equipmentCode;

    private String fromStageCode;
    private String toStageCode;

    @Indexed
    private String actionCode;

    private String actionName;
    private String previousStatus;
    private String newStatus;
    private String performedBy;
    private String performerName;
    private String performerRole;
    private String comments;
    private String justification;
    private Boolean esignatureVerified;
    private String esignatureReason;
    private String tenantId;
    private String plantId;
    private Instant timestamp;

    public WorkflowActionHistory() {}

    public static WorkflowActionHistoryBuilder builder() {
        return new WorkflowActionHistoryBuilder();
    }

    public static class WorkflowActionHistoryBuilder {
        private String id;
        private String historyId;
        private String instanceId;
        private String workflowCode;
        private String workflowVersion;
        private String entityId;
        private String batchNo;
        private String lotNo;
        private String equipmentCode;
        private String fromStageCode;
        private String toStageCode;
        private String actionCode;
        private String actionName;
        private String previousStatus;
        private String newStatus;
        private String performedBy;
        private String performerName;
        private String performerRole;
        private String comments;
        private String justification;
        private Boolean esignatureVerified;
        private String esignatureReason;
        private String tenantId;
        private String plantId;
        private Instant timestamp;

        public WorkflowActionHistoryBuilder id(String v) { this.id = v; return this; }
        public WorkflowActionHistoryBuilder historyId(String v) { this.historyId = v; return this; }
        public WorkflowActionHistoryBuilder instanceId(String v) { this.instanceId = v; return this; }
        public WorkflowActionHistoryBuilder workflowCode(String v) { this.workflowCode = v; return this; }
        public WorkflowActionHistoryBuilder workflowVersion(String v) { this.workflowVersion = v; return this; }
        public WorkflowActionHistoryBuilder entityId(String v) { this.entityId = v; return this; }
        public WorkflowActionHistoryBuilder batchNo(String v) { this.batchNo = v; return this; }
        public WorkflowActionHistoryBuilder lotNo(String v) { this.lotNo = v; return this; }
        public WorkflowActionHistoryBuilder equipmentCode(String v) { this.equipmentCode = v; return this; }
        public WorkflowActionHistoryBuilder fromStageCode(String v) { this.fromStageCode = v; return this; }
        public WorkflowActionHistoryBuilder toStageCode(String v) { this.toStageCode = v; return this; }
        public WorkflowActionHistoryBuilder actionCode(String v) { this.actionCode = v; return this; }
        public WorkflowActionHistoryBuilder actionName(String v) { this.actionName = v; return this; }
        public WorkflowActionHistoryBuilder previousStatus(String v) { this.previousStatus = v; return this; }
        public WorkflowActionHistoryBuilder newStatus(String v) { this.newStatus = v; return this; }
        public WorkflowActionHistoryBuilder performedBy(String v) { this.performedBy = v; return this; }
        public WorkflowActionHistoryBuilder performerName(String v) { this.performerName = v; return this; }
        public WorkflowActionHistoryBuilder performerRole(String v) { this.performerRole = v; return this; }
        public WorkflowActionHistoryBuilder comments(String v) { this.comments = v; return this; }
        public WorkflowActionHistoryBuilder justification(String v) { this.justification = v; return this; }
        public WorkflowActionHistoryBuilder esignatureVerified(Boolean v) { this.esignatureVerified = v; return this; }
        public WorkflowActionHistoryBuilder esignatureReason(String v) { this.esignatureReason = v; return this; }
        public WorkflowActionHistoryBuilder tenantId(String v) { this.tenantId = v; return this; }
        public WorkflowActionHistoryBuilder plantId(String v) { this.plantId = v; return this; }
        public WorkflowActionHistoryBuilder timestamp(Instant v) { this.timestamp = v; return this; }

        public WorkflowActionHistory build() {
            WorkflowActionHistory h = new WorkflowActionHistory();
            h.id = this.id;
            h.historyId = this.historyId;
            h.instanceId = this.instanceId;
            h.workflowCode = this.workflowCode;
            h.workflowVersion = this.workflowVersion;
            h.entityId = this.entityId;
            h.batchNo = this.batchNo;
            h.lotNo = this.lotNo;
            h.equipmentCode = this.equipmentCode;
            h.fromStageCode = this.fromStageCode;
            h.toStageCode = this.toStageCode;
            h.actionCode = this.actionCode;
            h.actionName = this.actionName;
            h.previousStatus = this.previousStatus;
            h.newStatus = this.newStatus;
            h.performedBy = this.performedBy;
            h.performerName = this.performerName;
            h.performerRole = this.performerRole;
            h.comments = this.comments;
            h.justification = this.justification;
            h.esignatureVerified = this.esignatureVerified;
            h.esignatureReason = this.esignatureReason;
            h.tenantId = this.tenantId;
            h.plantId = this.plantId;
            h.timestamp = this.timestamp;
            return h;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getWorkflowCode() { return workflowCode; }
    public void setWorkflowCode(String workflowCode) { this.workflowCode = workflowCode; }
    public String getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(String workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
    public String getFromStageCode() { return fromStageCode; }
    public void setFromStageCode(String fromStageCode) { this.fromStageCode = fromStageCode; }
    public String getToStageCode() { return toStageCode; }
    public void setToStageCode(String toStageCode) { this.toStageCode = toStageCode; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public String getPerformerName() { return performerName; }
    public void setPerformerName(String performerName) { this.performerName = performerName; }
    public String getPerformerRole() { return performerRole; }
    public void setPerformerRole(String performerRole) { this.performerRole = performerRole; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
    public Boolean getEsignatureVerified() { return esignatureVerified; }
    public void setEsignatureVerified(Boolean esignatureVerified) { this.esignatureVerified = esignatureVerified; }
    public String getEsignatureReason() { return esignatureReason; }
    public void setEsignatureReason(String esignatureReason) { this.esignatureReason = esignatureReason; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
