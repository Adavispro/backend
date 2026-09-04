package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.UnauthorizedException;
import com.adavis.security.PasswordEncoderConfig;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TopologyEsignatureServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private TopologyEsignatureService esignatureService;

    private static final String RAW_PASSWORD = "ValidPassword123!";
    private String encodedPassword;

    @BeforeEach
    void setUp() {
        encodedPassword = PasswordEncoderConfig.encode(RAW_PASSWORD);
    }

    @Test
    @DisplayName("validateRemarks throws REMARKS_REQUIRED on null or empty remarks")
    void validateRemarks_empty_throwsException() {
        BusinessException ex1 = assertThrows(BusinessException.class, () -> esignatureService.validateRemarks(null));
        assertEquals("REMARKS_REQUIRED", ex1.getErrorCode());

        BusinessException ex2 = assertThrows(BusinessException.class, () -> esignatureService.validateRemarks("   "));
        assertEquals("REMARKS_REQUIRED", ex2.getErrorCode());
    }

    @Test
    @DisplayName("validateRemarks throws REMARKS_TOO_LONG when exceeding 500 characters")
    void validateRemarks_tooLong_throwsException() {
        String longRemarks = "a".repeat(501);
        BusinessException ex = assertThrows(BusinessException.class, () -> esignatureService.validateRemarks(longRemarks));
        assertEquals("REMARKS_TOO_LONG", ex.getErrorCode());
    }

    @Test
    @DisplayName("validateRemarks succeeds on valid remarks")
    void validateRemarks_valid_succeeds() {
        assertDoesNotThrow(() -> esignatureService.validateRemarks("Routine topology configuration change"));
    }

    @Test
    @DisplayName("verifyEsignature throws ESIGN_PASSWORD_REQUIRED when password is missing")
    void verifyEsignature_missingPassword_throwsException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                esignatureService.verifyEsignature("USER-0001", "", "PLANT_CREATED", "TNT-0001"));
        assertEquals("ESIGN_PASSWORD_REQUIRED", ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyEsignature throws AUTH_CONTEXT_REQUIRED when userId is missing")
    void verifyEsignature_missingUserId_throwsException() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                esignatureService.verifyEsignature(null, RAW_PASSWORD, "PLANT_CREATED", "TNT-0001"));
        assertEquals("AUTH_CONTEXT_REQUIRED", ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyEsignature throws INVALID_ESIGN_CREDENTIALS when credential not found")
    void verifyEsignature_credentialNotFound_throwsException() {
        Document profileDoc = new Document("userId", "USER-0001")
                .append("isActive", true)
                .append("isBlocked", false)
                .append("tenantId", "TNT-0001");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_profiles")))
                .thenReturn(profileDoc);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_auth_credentials")))
                .thenReturn(null);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                esignatureService.verifyEsignature("USER-0001", RAW_PASSWORD, "PLANT_CREATED", "TNT-0001"));
        assertEquals("INVALID_ESIGN_CREDENTIALS", ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyEsignature throws USER_INACTIVE when user is deactivated")
    void verifyEsignature_inactiveUser_throwsException() {
        Document profileDoc = new Document("userId", "USER-0001")
                .append("isActive", false)
                .append("isBlocked", false)
                .append("tenantId", "TNT-0001");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_profiles")))
                .thenReturn(profileDoc);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                esignatureService.verifyEsignature("USER-0001", RAW_PASSWORD, "PLANT_CREATED", "TNT-0001"));
        assertEquals("USER_INACTIVE", ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyEsignature throws USER_BLOCKED when user is blocked")
    void verifyEsignature_blockedUser_throwsException() {
        Document profileDoc = new Document("userId", "USER-0001")
                .append("isActive", true)
                .append("isBlocked", true)
                .append("tenantId", "TNT-0001");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_profiles")))
                .thenReturn(profileDoc);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                esignatureService.verifyEsignature("USER-0001", RAW_PASSWORD, "PLANT_CREATED", "TNT-0001"));
        assertEquals("USER_BLOCKED", ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyEsignature throws UNAUTHORIZED_TENANT_ACCESS when user attempts cross-tenant signing")
    void verifyEsignature_crossTenant_throwsException() {
        Document profileDoc = new Document("userId", "USER-0001")
                .append("isActive", true)
                .append("isBlocked", false)
                .append("tenantId", "TNT-0001");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_profiles")))
                .thenReturn(profileDoc);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                esignatureService.verifyEsignature("USER-0001", RAW_PASSWORD, "PLANT_CREATED", "TNT-0002"));
        assertEquals("UNAUTHORIZED_TENANT_ACCESS", ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyEsignature throws INVALID_ESIGN_CREDENTIALS when password does not match")
    void verifyEsignature_invalidPassword_throwsException() {
        Document profileDoc = new Document("userId", "USER-0001")
                .append("isActive", true)
                .append("isBlocked", false)
                .append("tenantId", "TNT-0001");
        Document credentialDoc = new Document("userId", "USER-0001")
                .append("passwordHash", encodedPassword);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_profiles")))
                .thenReturn(profileDoc);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_auth_credentials")))
                .thenReturn(credentialDoc);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                esignatureService.verifyEsignature("USER-0001", "WrongPassword!", "PLANT_CREATED", "TNT-0001"));
        assertEquals("INVALID_ESIGN_CREDENTIALS", ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyEsignature succeeds when password matches")
    void verifyEsignature_validPassword_succeeds() {
        Document profileDoc = new Document("userId", "USER-0001")
                .append("isActive", true)
                .append("isBlocked", false)
                .append("tenantId", "TNT-0001");
        Document credentialDoc = new Document("userId", "USER-0001")
                .append("passwordHash", encodedPassword);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_profiles")))
                .thenReturn(profileDoc);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_auth_credentials")))
                .thenReturn(credentialDoc);

        boolean verified = esignatureService.verifyEsignature("USER-0001", RAW_PASSWORD, "PLANT_CREATED", "TNT-0001");
        assertTrue(verified);
    }
}
