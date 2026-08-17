package com.adavis.iiot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    private String type;             // WORKFLOW_TRANSITION, ASSIGNMENT, SYSTEM_ALERT
    private String eventCode;        // BATCH_SUBMITTED_FOR_REVIEW, BATCH_REVIEWED, BATCH_APPROVED, BATCH_REJECTED, WORKFLOW_ASSIGNMENT
    private String title;
    private String message;
    private String entityType;       // BATCH_STAGE, BATCH
    private String entityId;         // e.g. BATCH-G7-003:G7RMG
    private String batchNo;
    private String lotNo;
    private String equipmentCode;
    private String workflowState;    // UNDER_REVIEW, REVIEWER_REVIEWED, APPROVED, REJECTED
    private String severity;         // INFO, WARNING, SUCCESS, ERROR

    @Builder.Default
    private Boolean isRead = false;

    private Date createdAt;
    private Date readAt;
    private String actorUserId;

    private String idempotencyKey;
}
