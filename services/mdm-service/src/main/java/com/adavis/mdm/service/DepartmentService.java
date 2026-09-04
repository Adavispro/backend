package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.ResourceNotFoundException;
import com.adavis.mdm.model.entity.Department;
import com.adavis.mdm.repository.DepartmentRepository;
import com.adavis.mdm.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final BusinessIdGeneratorService businessIdGeneratorService;
    private final AuditEventPublisher auditEventPublisher;
    private final SecurityContextService securityContextService;
    private final TopologyEsignatureService topologyEsignatureService;

    @CacheEvict(value = "departments", allEntries = true)
    public Department createDepartment(Department department) {
        return createDepartment(department, "SYSTEM");
    }

    @CacheEvict(value = "departments", allEntries = true)
    public Department createDepartment(Department department, String actorUserId) {
        if (!StringUtils.hasText(department.getTenantId())) {
            throw new BusinessException("tenantId is required", "TENANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(department.getPlantId())) {
            throw new BusinessException("plantId is required", "PLANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(department.getDepartmentCode())) {
            throw new BusinessException("departmentCode is required", "DEPARTMENT_CODE_REQUIRED");
        }

        String effectiveTenantId = securityContextService.resolveEffectiveTenantId(actorUserId, department.getTenantId());
        department.setTenantId(effectiveTenantId);

        String remarks = StringUtils.hasText(department.getRemarks()) ? department.getRemarks() : department.getReason();
        String esignPassword = StringUtils.hasText(department.getEsignPassword()) ? department.getEsignPassword() : department.getPassword();
        enforceControlledAction(actorUserId, "DEPARTMENT_CREATED", null, remarks, esignPassword, department.getTenantId());

        department.setDepartmentId(businessIdGeneratorService.nextId("mdm_departments", "departmentId", "DEP-", 4));
        if (departmentRepository.existsByDepartmentId(department.getDepartmentId())) {
            throw new BusinessException("Department ID already exists: " + department.getDepartmentId(), 
                    "DUPLICATE_DEPARTMENT");
        }
        if (departmentRepository.existsByTenantIdAndPlantIdAndDepartmentCode(
                department.getTenantId(),
                department.getPlantId(),
                department.getDepartmentCode())) {
            throw new BusinessException("departmentCode already exists: " + department.getDepartmentCode(),
                    "DUPLICATE_RESOURCE");
        }

        normalizeDepartmentFields(department);

        Department parent = validateAndResolveParent(department.getDepartmentId(), department.getParentDepartmentId(), department.getTenantId(), department.getPlantId());
        if (parent != null) {
            department.setParentDepartmentId(parent.getDepartmentId());
            department.setPath(parent.getPath() + "/" + department.getDepartmentId());
        } else {
            department.setParentDepartmentId(null);
            department.setPath(department.getDepartmentId());
        }

        department.setIsActive(true);
        department.setCreatedAt(Instant.now());
        department.setUpdatedAt(Instant.now());

        log.info("Creating department: {} by actor: {}", department.getDepartmentId(), actorUserId);
        Department saved = departmentRepository.save(department);

        publishControlledAudit(actorUserId, "DEPARTMENT_CREATED", saved.getDepartmentId(), null, saved, remarks);
        return saved;
    }

    @Cacheable(value = "departments", key = "#departmentId")
    public Department getDepartmentByDepartmentId(String departmentId) {
        return departmentRepository.findByDepartmentId(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + departmentId));
    }

    public List<Department> getAllDepartments() {
        return getAllDepartments(null, null, null);
    }

    public List<Department> getAllDepartments(Boolean isActive) {
        return getAllDepartments(null, null, isActive);
    }

    public List<Department> getAllDepartments(String tenantId, String plantId, Boolean isActive) {
        if (StringUtils.hasText(tenantId) && StringUtils.hasText(plantId)) {
            if (isActive == null) {
                return departmentRepository.findByTenantIdAndPlantId(tenantId, plantId);
            }
            return departmentRepository.findByTenantIdAndPlantIdAndIsActive(tenantId, plantId, isActive);
        }
        if (StringUtils.hasText(tenantId)) {
            if (isActive == null) {
                return departmentRepository.findByTenantId(tenantId);
            }
            return departmentRepository.findByTenantIdAndIsActive(tenantId, isActive);
        }
        if (isActive == null) {
            return departmentRepository.findAll();
        }
        return departmentRepository.findByIsActive(isActive);
    }

    public List<Department> getActiveDepartments() {
        return getAllDepartments(null, null, true);
    }

    public List<Department> getDepartmentsByParent(String parentDepartmentId) {
        return departmentRepository.findByParentDepartmentIdAndIsActiveTrue(parentDepartmentId);
    }

    @CacheEvict(value = "departments", allEntries = true)
    public Department updateDepartment(String departmentId, Department updatedDepartment) {
        return updateDepartment(departmentId, updatedDepartment, "SYSTEM");
    }

    @CacheEvict(value = "departments", allEntries = true)
    public Department updateDepartment(String departmentId, Department updatedDepartment, String actorUserId) {
        Department existing = getDepartmentByDepartmentId(departmentId);
        Department beforeSnapshot = copyDepartment(existing);

        String tenantId = StringUtils.hasText(updatedDepartment.getTenantId()) ? updatedDepartment.getTenantId() : existing.getTenantId();
        String effectiveTenantId = securityContextService.resolveEffectiveTenantId(actorUserId, tenantId);
        String plantId = StringUtils.hasText(updatedDepartment.getPlantId()) ? updatedDepartment.getPlantId() : existing.getPlantId();
        String departmentCode = StringUtils.hasText(updatedDepartment.getDepartmentCode()) ? updatedDepartment.getDepartmentCode() : existing.getDepartmentCode();

        if (!StringUtils.hasText(effectiveTenantId)) {
            throw new BusinessException("tenantId is required", "TENANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(plantId)) {
            throw new BusinessException("plantId is required", "PLANT_ID_REQUIRED");
        }
        if (!StringUtils.hasText(departmentCode)) {
            throw new BusinessException("departmentCode is required", "DEPARTMENT_CODE_REQUIRED");
        }

        String remarks = StringUtils.hasText(updatedDepartment.getRemarks()) ? updatedDepartment.getRemarks() : updatedDepartment.getReason();
        String esignPassword = StringUtils.hasText(updatedDepartment.getEsignPassword()) ? updatedDepartment.getEsignPassword() : updatedDepartment.getPassword();
        enforceControlledAction(actorUserId, "DEPARTMENT_UPDATED", departmentId, remarks, esignPassword, effectiveTenantId);

        if (departmentRepository.existsByTenantIdAndPlantIdAndDepartmentCodeAndDepartmentIdNot(
                effectiveTenantId,
                plantId,
                departmentCode,
                departmentId)) {
            throw new BusinessException("departmentCode already exists: " + departmentCode,
                    "DUPLICATE_RESOURCE");
        }

        existing.setTenantId(effectiveTenantId);
        existing.setPlantId(plantId);
        existing.setDepartmentCode(departmentCode);
        existing.setDepartmentName(StringUtils.hasText(updatedDepartment.getDepartmentName()) ? updatedDepartment.getDepartmentName() : updatedDepartment.getName());
        existing.setName(updatedDepartment.getName());
        if (!StringUtils.hasText(existing.getName())) {
            existing.setName(existing.getDepartmentName());
        }
        if (updatedDepartment.getDescription() != null) {
            existing.setDescription(updatedDepartment.getDescription());
        }

        String oldPath = existing.getPath();
        String newParentId = updatedDepartment.getParentDepartmentId();
        Department parent = validateAndResolveParent(departmentId, newParentId, effectiveTenantId, plantId);
        String newPath;
        if (parent != null) {
            existing.setParentDepartmentId(parent.getDepartmentId());
            newPath = parent.getPath() + "/" + departmentId;
        } else {
            existing.setParentDepartmentId(null);
            newPath = departmentId;
        }
        existing.setPath(newPath);

        if (updatedDepartment.getIsActive() != null) {
            existing.setIsActive(updatedDepartment.getIsActive());
        }
        existing.setUpdatedAt(Instant.now());

        log.info("Updating department: {} by actor: {}", departmentId, actorUserId);
        Department saved = departmentRepository.save(existing);

        if (StringUtils.hasText(oldPath) && !oldPath.equals(newPath)) {
            updateDescendantPaths(departmentId, oldPath, newPath);
        }

        publishControlledAudit(actorUserId, "DEPARTMENT_UPDATED", departmentId, beforeSnapshot, saved, remarks);
        return saved;
    }

    @CacheEvict(value = "departments", allEntries = true)
    public void deleteDepartment(String departmentId) {
        deleteDepartment(departmentId, "SYSTEM", "Department deletion", null);
    }

    @CacheEvict(value = "departments", allEntries = true)
    public void deleteDepartment(String departmentId, String actorUserId, String remarks, String esignPassword) {
        Department department = getDepartmentByDepartmentId(departmentId);
        Department beforeSnapshot = copyDepartment(department);

        List<Department> children = departmentRepository.findByParentDepartmentId(departmentId);
        if (!children.isEmpty()) {
            throw new BusinessException("Cannot delete department with child departments", 
                    "DEPARTMENT_HAS_CHILDREN");
        }

        enforceControlledAction(actorUserId, "DEPARTMENT_DELETED", departmentId, remarks, esignPassword, department.getTenantId());

        department.setIsActive(false);
        department.setUpdatedAt(Instant.now());
        Department saved = departmentRepository.save(department);

        publishControlledAudit(actorUserId, "DEPARTMENT_DELETED", departmentId, beforeSnapshot, saved, remarks);
        log.info("Deleted department: {} by actor: {}", departmentId, actorUserId);
    }

    @CacheEvict(value = "departments", allEntries = true)
    public Department deactivateDepartment(String departmentId, String actorUserId, String remarks, String esignPassword) {
        Department department = getDepartmentByDepartmentId(departmentId);
        Department beforeSnapshot = copyDepartment(department);

        enforceControlledAction(actorUserId, "DEPARTMENT_DEACTIVATED", departmentId, remarks, esignPassword, department.getTenantId());

        department.setIsActive(false);
        department.setUpdatedAt(Instant.now());
        Department saved = departmentRepository.save(department);

        publishControlledAudit(actorUserId, "DEPARTMENT_DEACTIVATED", departmentId, beforeSnapshot, saved, remarks);
        log.info("Deactivated department: {} by actor: {}", departmentId, actorUserId);
        return saved;
    }

    @CacheEvict(value = "departments", allEntries = true)
    public Department reactivateDepartment(String departmentId) {
        return reactivateDepartment(departmentId, "SYSTEM", "Department reactivation", null);
    }

    @CacheEvict(value = "departments", allEntries = true)
    public Department reactivateDepartment(String departmentId, String actorUserId, String remarks, String esignPassword) {
        Department department = getDepartmentByDepartmentId(departmentId);
        Department beforeSnapshot = copyDepartment(department);

        if (StringUtils.hasText(department.getParentDepartmentId())) {
            Department parent = departmentRepository.findByDepartmentId(department.getParentDepartmentId()).orElse(null);
            if (parent != null && !Boolean.TRUE.equals(parent.getIsActive())) {
                throw new BusinessException("Cannot reactivate department because its parent department is inactive: " + parent.getDepartmentId(),
                        "INACTIVE_PARENT_DEPARTMENT");
            }
        }

        enforceControlledAction(actorUserId, "DEPARTMENT_REACTIVATED", departmentId, remarks, esignPassword, department.getTenantId());

        department.setIsActive(true);
        department.setUpdatedAt(Instant.now());
        Department saved = departmentRepository.save(department);

        publishControlledAudit(actorUserId, "DEPARTMENT_REACTIVATED", departmentId, beforeSnapshot, saved, remarks);
        log.info("Reactivated department: {} by actor: {}", departmentId, actorUserId);
        return saved;
    }

    public Map<String, Object> departmentSnapshot(Department department) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (department == null) {
            return snapshot;
        }
        snapshot.put("departmentId", department.getDepartmentId());
        snapshot.put("tenantId", department.getTenantId());
        snapshot.put("plantId", department.getPlantId());
        snapshot.put("departmentCode", department.getDepartmentCode());
        snapshot.put("departmentName", department.getDepartmentName());
        snapshot.put("name", department.getName());
        snapshot.put("description", department.getDescription());
        snapshot.put("parentDepartmentId", department.getParentDepartmentId());
        snapshot.put("path", department.getPath());
        snapshot.put("isActive", department.getIsActive());
        return snapshot;
    }

    private Department validateAndResolveParent(String targetDeptId, String parentDepartmentId, String tenantId, String plantId) {
        if (!StringUtils.hasText(parentDepartmentId)) {
            return null;
        }
        String trimmedParentId = parentDepartmentId.trim();
        if (StringUtils.hasText(targetDeptId) && trimmedParentId.equalsIgnoreCase(targetDeptId.trim())) {
            throw new BusinessException("Department cannot be its own parent: " + targetDeptId, "CIRCULAR_DEPARTMENT_HIERARCHY");
        }
        Department parent = departmentRepository.findByDepartmentId(trimmedParentId)
                .orElseThrow(() -> new BusinessException("Parent department not found: " + trimmedParentId, "INVALID_PARENT_DEPARTMENT"));

        if (!Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessException("Parent department must be active: " + trimmedParentId, "INACTIVE_PARENT_DEPARTMENT");
        }
        if (StringUtils.hasText(tenantId) && StringUtils.hasText(parent.getTenantId()) && !tenantId.equalsIgnoreCase(parent.getTenantId())) {
            throw new BusinessException("Parent department belongs to a different tenant: " + parent.getTenantId(), "INVALID_PARENT_DEPARTMENT");
        }
        if (StringUtils.hasText(plantId) && StringUtils.hasText(parent.getPlantId()) && !plantId.equalsIgnoreCase(parent.getPlantId())) {
            throw new BusinessException("Parent department belongs to a different plant: " + parent.getPlantId(), "INVALID_PARENT_DEPARTMENT");
        }

        // Check circular hierarchy by walking up ancestors of parent
        if (StringUtils.hasText(targetDeptId)) {
            String currentAncestorId = parent.getParentDepartmentId();
            int depth = 0;
            while (StringUtils.hasText(currentAncestorId) && depth < 50) {
                if (currentAncestorId.equalsIgnoreCase(targetDeptId.trim())) {
                    throw new BusinessException("Circular reference detected: " + targetDeptId + " is an ancestor of proposed parent " + trimmedParentId, "CIRCULAR_DEPARTMENT_HIERARCHY");
                }
                Department ancestor = departmentRepository.findByDepartmentId(currentAncestorId).orElse(null);
                if (ancestor == null) {
                    break;
                }
                currentAncestorId = ancestor.getParentDepartmentId();
                depth++;
            }
        }

        return parent;
    }

    private void updateDescendantPaths(String departmentId, String oldPathPrefix, String newPathPrefix) {
        if (!StringUtils.hasText(oldPathPrefix) || !StringUtils.hasText(newPathPrefix) || oldPathPrefix.equals(newPathPrefix)) {
            return;
        }
        List<Department> children = departmentRepository.findByParentDepartmentId(departmentId);
        for (Department child : children) {
            String childOldPath = child.getPath();
            String childNewPath = newPathPrefix + "/" + child.getDepartmentId();
            child.setPath(childNewPath);
            child.setUpdatedAt(Instant.now());
            departmentRepository.save(child);
            updateDescendantPaths(child.getDepartmentId(), childOldPath, childNewPath);
        }
    }

    private void enforceControlledAction(String actorUserId, String action, String departmentId, String remarks, String rawPassword, String tenantId) {
        topologyEsignatureService.validateRemarks(remarks);
        topologyEsignatureService.verifyEsignature(actorUserId, rawPassword, action + " on MDM_DEPARTMENT " + (departmentId != null ? departmentId : ""), tenantId);
    }

    private void publishControlledAudit(
            String actorUserId,
            String action,
            String departmentId,
            Department beforeDept,
            Department afterDept,
            String remarks) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.hasText(remarks)) {
            metadata.put("remarks", remarks.trim());
        }
        metadata.put("esignatureVerified", true);
        metadata.put("complianceStandard", "21_CFR_PART_11");
        metadata.put("verifiedAt", Instant.now().toString());

        Department ref = afterDept != null ? afterDept : beforeDept;
        if (ref != null) {
            if (StringUtils.hasText(ref.getDepartmentCode())) metadata.put("departmentCode", ref.getDepartmentCode());
            if (StringUtils.hasText(ref.getDepartmentName())) metadata.put("departmentName", ref.getDepartmentName());
            if (StringUtils.hasText(ref.getTenantId())) metadata.put("tenantId", ref.getTenantId());
            if (StringUtils.hasText(ref.getPlantId())) metadata.put("plantId", ref.getPlantId());
        }

        auditEventPublisher.publish(
                actorUserId,
                action,
                "MDM_DEPARTMENT",
                departmentId,
                "SUCCESS",
                departmentSnapshot(beforeDept),
                departmentSnapshot(afterDept),
                metadata
        );
    }

    private Department copyDepartment(Department d) {
        if (d == null) return null;
        return Department.builder()
                .id(d.getId())
                .departmentId(d.getDepartmentId())
                .tenantId(d.getTenantId())
                .plantId(d.getPlantId())
                .departmentCode(d.getDepartmentCode())
                .departmentName(d.getDepartmentName())
                .path(d.getPath())
                .name(d.getName())
                .description(d.getDescription())
                .parentDepartmentId(d.getParentDepartmentId())
                .isActive(d.getIsActive())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private void normalizeDepartmentFields(Department department) {
        if (!StringUtils.hasText(department.getDepartmentName()) && StringUtils.hasText(department.getName())) {
            department.setDepartmentName(department.getName());
        }
        if (!StringUtils.hasText(department.getName()) && StringUtils.hasText(department.getDepartmentName())) {
            department.setName(department.getDepartmentName());
        }
    }
}