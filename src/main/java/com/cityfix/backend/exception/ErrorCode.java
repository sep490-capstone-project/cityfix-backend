package com.cityfix.backend.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Email đã tồn tại"
    ),

    PHONE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Số điện thoại đã tồn tại"
    ),

    ACCOUNT_REACTIVATION_REQUIRED(
            HttpStatus.CONFLICT,
            "Tài khoản đã tồn tại nhưng đã bị xóa. Vui lòng gửi yêu cầu kích hoạt lại cho quản trị viên."
    ),

    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "Chưa xác thực"
    ),

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "Lỗi xác thực"
    ),

    ROLE_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Role not found"
    ),

    ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy tài khoản"
    ),

    CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy danh mục"
    ),

    ACCOUNT_ALREADY_VERIFIED(
            HttpStatus.CONFLICT,
            "Tài khoản đã được xác minh"
    ),

    INVALID_PASSWORD(
            HttpStatus.UNAUTHORIZED,
            "Sai mật khẩu"
    ),

    ACCOUNT_BANNED(
            HttpStatus.FORBIDDEN,
            "Tài khoản đã bị chặn"
    ),

    OTP_EXPIRED(
            HttpStatus.BAD_REQUEST,
            "OTP đã hết hạn hoặc không tìm thấy"
    ),

    OTP_INVALID(
            HttpStatus.BAD_REQUEST,
            "OTP không hợp lệ"
    ),

    OTP_COOLDOWN(
            HttpStatus.TOO_MANY_REQUESTS,
            "Vui lòng chờ trước khi yêu cầu mã OTP mới"
    ),

    INVALID_FILE_FORMAT(
            HttpStatus.BAD_REQUEST,
            "Định dạng tệp không hợp lệ. Chấp nhận các định dạng ảnh (JPG, PNG, WEBP, ...) và video (MP4, MOV, WEBM, ...)."
    ),

    FILE_SIZE_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "Kích thước tệp vượt quá giới hạn cho phép (Ảnh <= 10MB, Video <= 50MB)."
    ),

    FILE_COUNT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "Số lượng tệp vượt quá giới hạn tối đa cho phép (tối đa 7 tệp)."
    ),

    IMAGE_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Tải tệp lên thất bại. Vui lòng thử lại sau."
    ),

    DEPARTMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy cơ quan phụ trách"
    ),

    DEPARTMENT_NAME_EXISTS(
            HttpStatus.CONFLICT,
            "Tên cơ quan phụ trách đã tồn tại"
    ),

    ROUTING_RULE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy quy tắc định tuyến"
    ),

    ROUTING_RULE_CONFLICT(
            HttpStatus.CONFLICT,
            "Quy tắc định tuyến cho khu vực và loại sự cố này đã tồn tại"
    ),

    TICKET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy vé"
    ),

    TICKET_NOT_PENDING_REVIEW(
            HttpStatus.BAD_REQUEST,
            "Vé không ở trạng thái chờ duyệt"
    ),

    USER_NOT_IN_DEPARTMENT(
            HttpStatus.FORBIDDEN,
            "Người dùng không thuộc cơ quan / phòng ban nào"
    ),

    TICKET_INVALID_STATUS(
            HttpStatus.BAD_REQUEST,
            "Vé ở trạng thái không thể thực hiện thao tác này"
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "Không có quyền thực hiện thao tác này"
    ),

    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "Yêu cầu không hợp lệ"
    );

    private final HttpStatus status;
    private final String message;
}

