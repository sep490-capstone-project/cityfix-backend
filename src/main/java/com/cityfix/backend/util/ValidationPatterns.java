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
            "^\\d{10,11}$";

    public static final String PHONE_MESSAGE =
            "Số điện thoại không hợp lệ (phải gồm 10-11 chữ số)";

    public static final String PHONE_LOGIN_REGEX =
            "^(?:\\d{10,11}|\\+84\\d{9,10})$";

    public static final String PHONE_LOGIN_MESSAGE =
            "Số điện thoại không hợp lệ (gồm 10-11 chữ số, hoặc bắt đầu bằng +84 theo sau bởi 9-10 chữ số)";
}

