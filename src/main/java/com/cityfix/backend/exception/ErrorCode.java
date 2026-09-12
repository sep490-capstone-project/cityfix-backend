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
            "Định dạng tệp không hợp lệ. Chỉ chấp nhận JPG, JPEG, PNG, WEBP."
    ),

    FILE_SIZE_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "Kích thước tệp vượt quá giới hạn tối đa (5MB)."
    ),

    IMAGE_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Tải ảnh lên thất bại. Vui lòng thử lại sau."
    ),;

    private final HttpStatus status;
    private final String message;
}

