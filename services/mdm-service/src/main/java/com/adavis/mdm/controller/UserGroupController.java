package com.adavis.mdm.controller;

import com.adavis.common.dto.ApiResponse;
import com.adavis.mdm.model.entity.Group;
import com.adavis.mdm.security.InternalRequestValidator;
import com.adavis.mdm.security.SecurityContextService;
import com.adavis.mdm.service.UserGroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mdm/user-groups")
@RequiredArgsConstructor
public class UserGroupController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final UserGroupService userGroupService;
    private final InternalRequestValidator internalRequestValidator;
    private final SecurityContextService securityContextService;

    @PostMapping
    public ResponseEntity<ApiResponse<Group>> createGroup(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @Valid @RequestBody Group group,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Group created = userGroupService.createGroup(group, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Group created successfully", created));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Group>> getGroup(
            @PathVariable String groupId,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Group group = userGroupService.getGroupByGroupId(groupId, actor);
        return ResponseEntity.ok(ApiResponse.success(group));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Group>>> getAllGroups(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) Boolean isActive,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        List<Group> groups = userGroupService.getAllGroups(tenantId, isActive, actor);
        return ResponseEntity.ok(ApiResponse.success(groups));
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Group>> updateGroup(
            @PathVariable String groupId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            @Valid @RequestBody Group group,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Group updated = userGroupService.updateGroup(groupId, group, actor);
        return ResponseEntity.ok(ApiResponse.success("Group updated successfully", updated));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(
            @PathVariable String groupId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        userGroupService.deleteGroup(groupId, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Group deleted successfully"));
    }

    @PostMapping("/{groupId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateGroup(
            @PathVariable String groupId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        userGroupService.deleteGroup(groupId, actor);
        return ResponseEntity.ok(ApiResponse.successMessage("Group deactivated successfully"));
    }

    @PostMapping("/{groupId}/activate")
    public ResponseEntity<ApiResponse<Group>> reactivateGroup(
            @PathVariable String groupId,
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String internalAuth,
            @RequestHeader(value = USER_ID_HEADER, required = false) String currentUserId,
            HttpServletRequest httpRequest) {
        internalRequestValidator.validateInternalGatewayRequest(internalAuth);
        String actor = securityContextService.resolveActor(currentUserId, httpRequest);
        Group reactivated = userGroupService.reactivateGroup(groupId, actor);
        return ResponseEntity.ok(ApiResponse.success("Group reactivated successfully", reactivated));
    }
}