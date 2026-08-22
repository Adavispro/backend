package com.adavis.iiot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "iiot_workflow_instances")
public class WorkflowInstance {

    @JsonIgnore
    @Id
    private String id;

    @Indexed(unique = true)
    private String instanceId;

    @Indexed
    private String workflowCode;

    private String workflowVersion;

    @Indexed
    private String entityType;

    @Indexed
    private String entityId;

    @Indexed
    private String batchNo;

    @Indexed
    private String lotNo;

    @Indexed
    private String equipmentCode;

    @Indexed
    private String tenantId;

    @Indexed
    private String plantId;

    @Indexed
    private String currentStageCode;

    @Indexed
    private String currentStatus;

    private String initiatedBy;
    private Instant initiatedAt;
    private String lastActionCode;
    private String lastActionBy;
    private Instant lastActionAt;
    private String assignedTo;
    private Boolean isTerminal;
    private Map<String, Object> context;
    private Instant createdAt;
    private Instant updatedAt;

    public WorkflowInstance() {}

    public static WorkflowInstanceBuilder builder() {
        return new WorkflowInstanceBuilder();
    }

    public static class WorkflowInstanceBuilder {
        private String id;
        private String instanceId;
        private String workflowCode;
        private String workflowVersion;
        private String entityType;
        private String entityId;
        private String batchNo;
        private String lotNo;
        private String equipmentCode;
        private String tenantId;
        private String plantId;
        private String currentStageCode;
        private String currentStatus;
        private String initiatedBy;
        private Instant initiatedAt;
        private String lastActionCode;
        private String lastActionBy;
        private Instant lastActionAt;
        private String assignedTo;
        private Boolean isTerminal;
        private Map<String, Object> context;
        private Instant createdAt;
        private Instant updatedAt;

        public WorkflowInstanceBuilder id(String v) { this.id = v; return this; }
        public WorkflowInstanceBuilder instanceId(String v) { this.instanceId = v; return this; }
        public WorkflowInstanceBuilder workflowCode(String v) { this.workflowCode = v; return this; }
        public WorkflowInstanceBuilder workflowVersion(String v) { this.workflowVersion = v; return this; }
        public WorkflowInstanceBuilder entityType(String v) { this.entityType = v; return this; }
        public WorkflowInstanceBuilder entityId(String v) { this.entityId = v; return this; }
        public WorkflowInstanceBuilder batchNo(String v) { this.batchNo = v; return this; }
        public WorkflowInstanceBuilder lotNo(String v) { this.lotNo = v; return this; }
        public WorkflowInstanceBuilder equipmentCode(String v) { this.equipmentCode = v; return this; }
        public WorkflowInstanceBuilder tenantId(String v) { this.tenantId = v; return this; }
        public WorkflowInstanceBuilder plantId(String v) { this.plantId = v; return this; }
        public WorkflowInstanceBuilder currentStageCode(String v) { this.currentStageCode = v; return this; }
        public WorkflowInstanceBuilder currentStatus(String v) { this.currentStatus = v; return this; }
        public WorkflowInstanceBuilder initiatedBy(String v) { this.initiatedBy = v; return this; }
        public WorkflowInstanceBuilder initiatedAt(Instant v) { this.initiatedAt = v; return this; }
        public WorkflowInstanceBuilder lastActionCode(String v) { this.lastActionCode = v; return this; }
        public WorkflowInstanceBuilder lastActionBy(String v) { this.lastActionBy = v; return this; }
        public WorkflowInstanceBuilder lastActionAt(Instant v) { this.lastActionAt = v; return this; }
        public WorkflowInstanceBuilder assignedTo(String v) { this.assignedTo = v; return this; }
        public WorkflowInstanceBuilder isTerminal(Boolean v) { this.isTerminal = v; return this; }
        public WorkflowInstanceBuilder context(Map<String, Object> v) { this.context = v; return this; }
        public WorkflowInstanceBuilder createdAt(Instant v) { this.createdAt = v; return this; }
        public WorkflowInstanceBuilder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public WorkflowInstance build() {
            WorkflowInstance w = new WorkflowInstance();
            w.id = this.id;
            w.instanceId = this.instanceId;
            w.workflowCode = this.workflowCode;
            w.workflowVersion = this.workflowVersion;
            w.entityType = this.entityType;
            w.entityId = this.entityId;
            w.batchNo = this.batchNo;
            w.lotNo = this.lotNo;
            w.equipmentCode = this.equipmentCode;
            w.tenantId = this.tenantId;
            w.plantId = this.plantId;
            w.currentStageCode = this.currentStageCode;
            w.currentStatus = this.currentStatus;
            w.initiatedBy = this.initiatedBy;
            w.initiatedAt = this.initiatedAt;
            w.lastActionCode = this.lastActionCode;
            w.lastActionBy = this.lastActionBy;
            w.lastActionAt = this.lastActionAt;
            w.assignedTo = this.assignedTo;
            w.isTerminal = this.isTerminal;
            w.context = this.context;
            w.createdAt = this.createdAt;
            w.updatedAt = this.updatedAt;
            return w;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getWorkflowCode() { return workflowCode; }
    public void setWorkflowCode(String workflowCode) { this.workflowCode = workflowCode; }
    public String getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(String workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getCurrentStageCode() { return currentStageCode; }
    public void setCurrentStageCode(String currentStageCode) { this.currentStageCode = currentStageCode; }
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant initiatedAt) { this.initiatedAt = initiatedAt; }
    public String getLastActionCode() { return lastActionCode; }
    public void setLastActionCode(String lastActionCode) { this.lastActionCode = lastActionCode; }
    public String getLastActionBy() { return lastActionBy; }
    public void setLastActionBy(String lastActionBy) { this.lastActionBy = lastActionBy; }
    public Instant getLastActionAt() { return lastActionAt; }
    public void setLastActionAt(Instant lastActionAt) { this.lastActionAt = lastActionAt; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Boolean getIsTerminal() { return isTerminal; }
    public void setIsTerminal(Boolean isTerminal) { this.isTerminal = isTerminal; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
