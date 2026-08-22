package com.adavis.iiot.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "notifications")
@CompoundIndexes({
    @CompoundIndex(name = "idx_recipient_read_created", def = "{'recipientUserId': 1, 'isRead': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "idx_idempotency", def = "{'idempotencyKey': 1}", unique = true, sparse = true)
})
public class NotificationDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String notificationId;

    @Indexed
    private String recipientUserId;

    @Indexed
    private String tenantId;

    private String plantId;
    private String type;
    private String eventCode;
    private String title;
    private String message;
    private String entityType;
    private String entityId;
    private String batchNo;
    private String lotNo;
    private String equipmentCode;
    private String workflowState;
    private String severity;
    private Boolean isRead = false;
    private Date createdAt;
    private Date readAt;
    private String actorUserId;
    private String idempotencyKey;

    public NotificationDocument() {}

    public static NotificationDocumentBuilder builder() {
        return new NotificationDocumentBuilder();
    }

    public static class NotificationDocumentBuilder {
        private String id;
        private String notificationId;
        private String recipientUserId;
        private String tenantId;
        private String plantId;
        private String type;
        private String eventCode;
        private String title;
        private String message;
        private String entityType;
        private String entityId;
        private String batchNo;
        private String lotNo;
        private String equipmentCode;
        private String workflowState;
        private String severity;
        private Boolean isRead = false;
        private Date createdAt;
        private Date readAt;
        private String actorUserId;
        private String idempotencyKey;

        public NotificationDocumentBuilder id(String v) { this.id = v; return this; }
        public NotificationDocumentBuilder notificationId(String v) { this.notificationId = v; return this; }
        public NotificationDocumentBuilder recipientUserId(String v) { this.recipientUserId = v; return this; }
        public NotificationDocumentBuilder tenantId(String v) { this.tenantId = v; return this; }
        public NotificationDocumentBuilder plantId(String v) { this.plantId = v; return this; }
        public NotificationDocumentBuilder type(String v) { this.type = v; return this; }
        public NotificationDocumentBuilder eventCode(String v) { this.eventCode = v; return this; }
        public NotificationDocumentBuilder title(String v) { this.title = v; return this; }
        public NotificationDocumentBuilder message(String v) { this.message = v; return this; }
        public NotificationDocumentBuilder entityType(String v) { this.entityType = v; return this; }
        public NotificationDocumentBuilder entityId(String v) { this.entityId = v; return this; }
        public NotificationDocumentBuilder batchNo(String v) { this.batchNo = v; return this; }
        public NotificationDocumentBuilder lotNo(String v) { this.lotNo = v; return this; }
        public NotificationDocumentBuilder equipmentCode(String v) { this.equipmentCode = v; return this; }
        public NotificationDocumentBuilder workflowState(String v) { this.workflowState = v; return this; }
        public NotificationDocumentBuilder severity(String v) { this.severity = v; return this; }
        public NotificationDocumentBuilder isRead(Boolean v) { this.isRead = v; return this; }
        public NotificationDocumentBuilder createdAt(Date v) { this.createdAt = v; return this; }
        public NotificationDocumentBuilder readAt(Date v) { this.readAt = v; return this; }
        public NotificationDocumentBuilder actorUserId(String v) { this.actorUserId = v; return this; }
        public NotificationDocumentBuilder idempotencyKey(String v) { this.idempotencyKey = v; return this; }

        public NotificationDocument build() {
            NotificationDocument doc = new NotificationDocument();
            doc.id = this.id;
            doc.notificationId = this.notificationId;
            doc.recipientUserId = this.recipientUserId;
            doc.tenantId = this.tenantId;
            doc.plantId = this.plantId;
            doc.type = this.type;
            doc.eventCode = this.eventCode;
            doc.title = this.title;
            doc.message = this.message;
            doc.entityType = this.entityType;
            doc.entityId = this.entityId;
            doc.batchNo = this.batchNo;
            doc.lotNo = this.lotNo;
            doc.equipmentCode = this.equipmentCode;
            doc.workflowState = this.workflowState;
            doc.severity = this.severity;
            doc.isRead = this.isRead;
            doc.createdAt = this.createdAt;
            doc.readAt = this.readAt;
            doc.actorUserId = this.actorUserId;
            doc.idempotencyKey = this.idempotencyKey;
            return doc;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    public String getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(String recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
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
    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getReadAt() { return readAt; }
    public void setReadAt(Date readAt) { this.readAt = readAt; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
