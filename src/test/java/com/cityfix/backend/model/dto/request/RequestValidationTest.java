package com.cityfix.backend.model.dto.request;

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

        // Malformed email must fail (REQ_51 UTCID04)
        UserRegisterRequest badEmailReq = UserRegisterRequest.builder()
                .email("invalid-email")
                .password("CityFix@2026")
                .fullName("Nguyễn Văn A")
                .phoneNumber("0912345678")
                .province("TP. Hồ Chí Minh")
                .ward("Phường 1")
                .address("123 Lê Lợi")
                .build();
        assertThat(validator.validate(badEmailReq))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("email");
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
    @DisplayName("createTicketRequest_latitudeAbove90_violatesRangeConstraint")
    void createTicketRequest_latitudeAbove90_violatesRangeConstraint() {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setDescription("Sụt lún mặt đường");
        req.setSubCategoryIds(List.of(UUID.randomUUID()));
        req.setPriority(Priority.HIGH);
        req.setLocationMethod(LocationMethod.AUTO_GPS);
        req.setAddress("123 Lê Lợi, Quận 1");
        req.setLatitude(95.0);
        req.setLongitude(106.70);

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(req);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("latitude");
    }

    @Test
    @DisplayName("createTicketRequest_longitudeBelowMinus180_violatesRangeConstraint")
    void createTicketRequest_longitudeBelowMinus180_violatesRangeConstraint() {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setDescription("Sụt lún mặt đường");
        req.setSubCategoryIds(List.of(UUID.randomUUID()));
        req.setPriority(Priority.HIGH);
        req.setLocationMethod(LocationMethod.AUTO_GPS);
        req.setAddress("123 Lê Lợi, Quận 1");
        req.setLatitude(10.77);
        req.setLongitude(-185.0);

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(req);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("longitude");
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
        UpdateDepartmentRequest badDept = new UpdateDepartmentRequest("", "bad-email", "00123", true);
        assertThat(validator.validate(badDept)).hasSizeGreaterThanOrEqualTo(3);

        UpdateDepartmentRequest goodDept = new UpdateDepartmentRequest(
                "Phòng Quản lý Đô thị", "qldt@cityfix.vn", "02838223344", true);
        assertThat(validator.validate(goodDept)).isEmpty();

        UpdateRoutingRuleRequest badRule = new UpdateRoutingRuleRequest(null, null, " ", "");
        assertThat(validator.validate(badRule)).hasSize(4);
    }

    @Test
    @DisplayName("createRatingRequest_enforcesRateBetween1And5")
    void createRatingRequest_enforcesRateBetween1And5() {
        CreateRatingRequest valid = CreateRatingRequest.builder()
                .rate(5)
                .feedback("Fast resolution")
                .isAnonymous(false)
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        CreateRatingRequest tooLow = CreateRatingRequest.builder().rate(0).build();
        assertThat(validator.validate(tooLow))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("rate");

        CreateRatingRequest tooHigh = CreateRatingRequest.builder().rate(6).build();
        assertThat(validator.validate(tooHigh))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("rate");
    }

    @Test
    @DisplayName("createDepartmentRequest_rejectsBlankNameAndMalformedContactEmail")
    void createDepartmentRequest_rejectsBlankNameAndMalformedContactEmail() {
        CreateDepartmentRequest valid = new CreateDepartmentRequest(
                "Urban Infrastructure Department",
                "infra@cityfix.vn",
                "0901234567"
        );
        assertThat(validator.validate(valid)).isEmpty();

        CreateDepartmentRequest blankName = new CreateDepartmentRequest(
                "   ",
                "infra@cityfix.vn",
                "0901234567"
        );
        assertThat(validator.validate(blankName))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("name");

        CreateDepartmentRequest badEmail = new CreateDepartmentRequest(
                "Urban Infrastructure Department",
                "invalid-email-format",
                "0901234567"
        );
        assertThat(validator.validate(badEmail))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("contactEmail");
    }

    @Test
    @DisplayName("resetPasswordRequest_rejectsBlankOrWeakNewPassword")
    void resetPasswordRequest_rejectsBlankOrWeakNewPassword() {
        ResetPasswordRequest valid = ResetPasswordRequest.builder()
                .firebaseIdToken("valid-firebase-id-token")
                .newPassword("NewSecret@123")
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        ResetPasswordRequest blankPassword = ResetPasswordRequest.builder()
                .firebaseIdToken("valid-firebase-id-token")
                .newPassword("   ")
                .build();
        assertThat(validator.validate(blankPassword))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("newPassword");

        ResetPasswordRequest weakPassword = ResetPasswordRequest.builder()
                .firebaseIdToken("valid-firebase-id-token")
                .newPassword("123")
                .build();
        assertThat(validator.validate(weakPassword))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("newPassword");
    }

    @Test
    @DisplayName("categoryRequest_rejectsBlankNameAndNameExceeding150Chars")
    void categoryRequest_rejectsBlankNameAndNameExceeding150Chars() {
        CategoryRequest valid = CategoryRequest.builder()
                .name("Hạ tầng cầu đường")
                .description("Mô tả danh mục")
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        CategoryRequest blankName = CategoryRequest.builder()
                .name("   ")
                .description("Mô tả")
                .build();
        assertThat(validator.validate(blankName))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("name");

        CategoryRequest tooLongName = CategoryRequest.builder()
                .name("A".repeat(151))
                .description("Mô tả")
                .build();
        assertThat(validator.validate(tooLongName))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    @DisplayName("phoneLoginRequest_validatesPhoneNumberAndNotBlankPasswordWithoutBlockingLegacyPasswords")
    void phoneLoginRequest_validatesPhoneNumberAndNotBlankPasswordWithoutBlockingLegacyPasswords() {
        PhoneLoginRequest validLocal = PhoneLoginRequest.builder()
                .phoneNumber("0912345678")
                .password("123456")
                .build();
        assertThat(validator.validate(validLocal)).isEmpty();

        PhoneLoginRequest validE164 = PhoneLoginRequest.builder()
                .phoneNumber("+84912345678")
                .password("123456")
                .build();
        assertThat(validator.validate(validE164)).isEmpty();

        PhoneLoginRequest invalid = PhoneLoginRequest.builder()
                .phoneNumber("12345")
                .password("   ")
                .build();
        Set<ConstraintViolation<PhoneLoginRequest>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("phoneNumber", "password");
    }

    @Test
    @DisplayName("updatePhoneRequest_rejectsBlankFirebaseIdToken")
    void updatePhoneRequest_rejectsBlankFirebaseIdToken() {
        UpdatePhoneRequest valid = UpdatePhoneRequest.builder()
                .firebaseIdToken("eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ.payload.sig")
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        UpdatePhoneRequest invalid = UpdatePhoneRequest.builder()
                .firebaseIdToken("   ")
                .build();
        Set<ConstraintViolation<UpdatePhoneRequest>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("firebaseIdToken");
    }

    @Test
    @DisplayName("updateProfileRequest_validatesFourBasicFieldsWithoutPhoneNumber")
    void updateProfileRequest_validatesFourBasicFieldsWithoutPhoneNumber() {
        UpdateProfileRequest valid = UpdateProfileRequest.builder()
                .fullName("Nguyễn Văn A")
                .address("123 Lê Lợi")
                .province("TP. Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        UpdateProfileRequest invalid = UpdateProfileRequest.builder()
                .fullName("   ")
                .address("")
                .province("   ")
                .ward("")
                .build();
        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(invalid);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("fullName", "address", "province", "ward");
    }
}

