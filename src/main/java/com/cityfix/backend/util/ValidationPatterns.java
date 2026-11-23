package com.cityfix.backend.util;

public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    public static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{10,100}$";

    public static final String OPTIONAL_PASSWORD_REGEX =
            "^(?:$|(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{10,100})$";

    public static final String PASSWORD_MESSAGE =
            "Mật khẩu phải từ 10 đến 100 ký tự, không chứa khoảng trắng, bao gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt";

    public static final String PHONE_REGEX =
            "^(0|\\+84)[1-9]\\d{8,9}$";

    public static final String PHONE_MESSAGE =
            "Số điện thoại không hợp lệ (gồm 10-11 chữ số, bắt đầu bằng 0 hoặc +84 theo sau bởi chữ số khác 0)";
}

