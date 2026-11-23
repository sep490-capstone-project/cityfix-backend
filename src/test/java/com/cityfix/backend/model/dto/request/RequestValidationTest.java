package com.cityfix.backend.model.dto.request;

import com.cityfix.backend.model.enums.DepartmentLevel;
import com.cityfix.backend.model.enums.LocationMethod;
import com.cityfix.backend.model.enums.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("loginRequest_validatesEmailAndNotBlankPasswordWithoutBlockingLegacyPasswords")
    void loginRequest_validatesEmailAndNotBlankPasswordWithoutBlockingLegacyPasswords() {
        LoginRequest validLegacy = LoginRequest.builder()
                .email("admin.test@cityfix.vn")
                .password("123456")
                .build();
        assertThat(validator.validate(validLegacy)).isEmpty();

        LoginRequest invalid = LoginRequest.builder()
                .email("not-an-email")
                .password("   ")
                .build();
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("email", "password");
    }

    @Test
    @DisplayName("userRegisterRequest_enforcesStrongPasswordNoWhitespaceValidPhoneAndAddressFields")
    void userRegisterRequest_enforcesStrongPasswordNoWhitespaceValidPhoneAndAddressFields() {
        UserRegisterRequest valid = UserRegisterRequest.builder()
                .email("citizen@cityfix.vn")
                .password("CityFix@2026")
                .fullName("Nguyễn Văn A")
                .phoneNumber("0912345678")
                .province("TP. Hồ Chí Minh")
                .ward("Phường 1")
                .address("123 Lê Lợi")
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        // Password with space or < 10 chars or missing special char must fail
        for (String badPass : List.of("Short1!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecialChar123", "Abcdef1!  ")) {
            UserRegisterRequest req = UserRegisterRequest.builder()
                    .email("citizen@cityfix.vn")
                    .password(badPass)
                    .fullName("Nguyễn Văn A")
                    .phoneNumber("+84912345678")
                    .province("TP. Hồ Chí Minh")
                    .ward("Phường 1")
                    .address("123 Lê Lợi")
                    .build();
            assertThat(validator.validate(req))
                    .as("Password '%s' should be rejected", badPass)
                    .isNotEmpty();
        }

        // Invalid phone numbers (+8409... and 00...) must fail
        for (String badPhone : List.of("+84091234567", "0012345678", "12345")) {
            UserRegisterRequest req = UserRegisterRequest.builder()
                    .email("citizen@cityfix.vn")
                    .password("CityFix@2026")
                    .fullName("Nguyễn Văn A")
                    .phoneNumber(badPhone)
                    .province("TP. Hồ Chí Minh")
                    .ward("Phường 1")
                    .address("123 Lê Lợi")
                    .build();
            assertThat(validator.validate(req))
                    .as("Phone '%s' should be rejected", badPhone)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("adminUserRequest_allowsEmptyOrNullPasswordForUpdate_rejectsWeakPasswordAndNullDeptElement")
    void adminUserRequest_allowsEmptyOrNullPasswordForUpdate_rejectsWeakPasswordAndNullDeptElement() {
        AdminUserRequest validEmptyPass = AdminUserRequest.builder()
                .email("staff@cityfix.vn")
                .password("")
                .fullName("Lê Nhân Viên")
                .phoneNumber("02838123456")
                .province("TP. Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .address("01 Lê Duẩn")
                .roleId(UUID.randomUUID())
                .build();
        assertThat(validator.validate(validEmptyPass)).isEmpty();

        validEmptyPass.setPassword("weak123");
        assertThat(validator.validate(validEmptyPass)).isNotEmpty();

        validEmptyPass.setPassword("StrongPass@2026");
        validEmptyPass.setDepartmentIds(Arrays.asList(UUID.randomUUID(), null));
        Set<ConstraintViolation<AdminUserRequest>> deptViolations = validator.validate(validEmptyPass);
        assertThat(deptViolations).anyMatch(v -> v.getPropertyPath().toString().contains("departmentIds"));
    }

    @Test
    @DisplayName("createTicketRequest_validatesAddressMax255ImageUrlsMax7ConfidenceScoreAndNonNullSubCategories")
    void createTicketRequest_validatesAddressMax255ImageUrlsMax7ConfidenceScoreAndNonNullSubCategories() {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setDescription("Hố ga mất nắp nguy hiểm");
        req.setSubCategoryIds(Arrays.asList(UUID.randomUUID(), null));
        req.setPriority(Priority.HIGH);
        req.setLocationMethod(LocationMethod.AUTO_GPS);
        req.setAddress("A".repeat(256));
        req.setLatitude(10.77);
        req.setLongitude(106.70);
        req.setConfidenceScore(105.5);
        req.setImageUrls(List.of("u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8"));

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(req);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("address", "confidenceScore", "imageUrls", "subCategoryIds[1].<list element>");
    }

    @Test
    @DisplayName("subCategoryAndStepRequests_allowZeroSlaAndEnforcePositiveStepDuration")
    void subCategoryAndStepRequests_allowZeroSlaAndEnforcePositiveStepDuration() {
        SubCategoryRequest subReq = new SubCategoryRequest();
        subReq.setCategoryId(UUID.randomUUID());
        subReq.setName("Ổ gà");
        subReq.setSla(0);
        subReq.setPriority(Priority.MEDIUM);
        subReq.setIsActive(true);
        assertThat(validator.validate(subReq)).isEmpty();

        subReq.setSla(-1);
        assertThat(validator.validate(subReq)).isNotEmpty();

        StepRequest stepReq = new StepRequest();
        stepReq.setSubCategoryId(UUID.randomUUID());
        stepReq.setStepName("Khảo sát");
        stepReq.setDuration(0);
        assertThat(validator.validate(stepReq)).isNotEmpty();
    }

    @Test
    @DisplayName("reviewTicketAndStepRequests_validateListElementsAndFutureDeadlines")
    void reviewTicketAndStepRequests_validateListElementsAndFutureDeadlines() {
        ReviewTicketRequest.StepAssignmentRequest pastStep = ReviewTicketRequest.StepAssignmentRequest.builder()
                .stepId(UUID.randomUUID())
                .assigneeId(UUID.randomUUID())
                .dependsOnStepIds(Arrays.asList(UUID.randomUUID(), null))
                .deadline(LocalDateTime.now().minusHours(1))
                .build();

        ReviewTicketRequest reviewReq = ReviewTicketRequest.builder()
                .subCategoryIds(List.of(UUID.randomUUID()))
                .steps(Arrays.asList(pastStep, null))
                .build();

        Set<ConstraintViolation<ReviewTicketRequest>> violations = validator.validate(reviewReq);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("steps[0].deadline", "steps[0].dependsOnStepIds[1].<list element>", "steps[1].<list element>");

        UpdateTicketStepDependenciesRequest depReq = new UpdateTicketStepDependenciesRequest(Arrays.asList(UUID.randomUUID(), null));
        assertThat(validator.validate(depReq)).isNotEmpty();
    }

    @Test
    @DisplayName("departmentAndRoutingRuleUpdateRequests_enforceRequiredFields")
    void departmentAndRoutingRuleUpdateRequests_enforceRequiredFields() {
        UpdateDepartmentRequest badDept = new UpdateDepartmentRequest("", null, "bad-email", "00123", true);
        assertThat(validator.validate(badDept)).hasSizeGreaterThanOrEqualTo(4);

        UpdateDepartmentRequest goodDept = new UpdateDepartmentRequest(
                "Phòng Quản lý Đô thị", DepartmentLevel.WARD, "qldt@cityfix.vn", "02838223344", true);
        assertThat(validator.validate(goodDept)).isEmpty();

        UpdateRoutingRuleRequest badRule = new UpdateRoutingRuleRequest(null, null, " ", "");
        assertThat(validator.validate(badRule)).hasSize(4);
    }
}

