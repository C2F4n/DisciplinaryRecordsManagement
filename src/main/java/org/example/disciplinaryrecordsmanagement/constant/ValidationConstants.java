package org.example.disciplinaryrecordsmanagement.constant;

public final class ValidationConstants {
    public static final int MAX_STUDENT_NUMBER_LENGTH = 32;
    public static final String STUDENT_NUMBER = "[0-9]{1," + MAX_STUDENT_NUMBER_LENGTH + "}";
    public static final String STUDENT_NUMBER_PREFIX = "[0-9]{0," + MAX_STUDENT_NUMBER_LENGTH + "}";
    public static final String STAFF_NAME = "[a-z][a-z0-9_.-]{3,47}";
    public static final String PHONE = "1[0-9]{10}";
    public static final String OPTIONAL_PHONE = "(?:" + PHONE + ")?";
    public static final String SUBMISSION_ID = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 18;
    public static final int MAX_PASSWORD_BYTES = 72;
    public static final String PASSWORD_PATTERN = "(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9_@]{"
            + MIN_PASSWORD_LENGTH + "," + MAX_PASSWORD_LENGTH + "}";
    public static final String LOCAL_BOOTSTRAP_PIN_PATTERN = "[0-9]{" + MIN_PASSWORD_LENGTH + "}";
    public static final int MAX_USERNAME_LENGTH = 48;
    public static final int MAX_STUDENT_LOOKUP_LENGTH = 80;
    public static final int MAX_STUDENT_NAME_LENGTH = 80;
    public static final int MAX_CLASS_NAME_LENGTH = 100;
    public static final int MAX_COUNSELOR_NAME_LENGTH = 80;
    public static final int MAX_CLASS_COUNSELORS = 3;
    public static final int MAX_REASON_LENGTH = 500;
    public static final int MAX_VIOLATION_TYPE_LENGTH = 30;
    public static final int MAX_DEPARTMENT_CODE_LENGTH = 24;
    public static final int MAX_WORKFLOW_CODE_LENGTH = 16;
    public static final int MAX_WORKFLOW_LABEL_LENGTH = 80;
    public static final int MAX_VIOLATION_NOTE_LENGTH = 250;

    public static final int EMAIL_CODE_LENGTH = 6;
    public static final String EMAIL_CODE_PATTERN = "[0-9]{" + EMAIL_CODE_LENGTH + "}";
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final String SINGLE_EMAIL = "[^\\s,;<>]+";
    public static final int MAX_PERMISSION_NOTE_LENGTH = 250;
    public static final int MAX_GRANT_SCOPE_LENGTH = 32;
    public static final String DEFAULT_PAGE = "1";
    public static final String DEFAULT_PAGE_SIZE = "20";
    public static final int MAX_PAGE = 100_000;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_REVIEW_ITEMS = 100;
    public static final int MAX_TREND_BUCKETS = 24;
    public static final String DEFAULT_STATISTICS_PERIOD = "WEEK";

    private ValidationConstants() {}
}
