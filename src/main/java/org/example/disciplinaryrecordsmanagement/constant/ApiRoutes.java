package org.example.disciplinaryrecordsmanagement.constant;

public final class ApiRoutes {
    public static final String ROOT = "/api";
    public static final String PATTERN = ROOT + "/**";
    public static final String CSRF = "/csrf";
    public static final String BIND = "/bind";
    public static final String BIND_EMAIL_CODE = BIND + "/email-code";
    public static final String BINDING_CLASSES = "/binding-classes";
    public static final String PASSWORD = "/password";
    public static final String LOGIN = ROOT + "/login";
    public static final String LOGOUT = ROOT + "/logout";

    private ApiRoutes() {}
}
