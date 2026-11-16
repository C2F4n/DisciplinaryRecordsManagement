package org.example.disciplinaryrecordsmanagement.model;

public enum ViolationStatus {
    PENDING("待审核"), APPROVED("已审核"), HANDED_BACK("已交回"), APPEALED("已申诉"), RECORDED("已录入");

    private final String label;

    ViolationStatus(String label) { this.label = label; }

    public String label() { return label; }
}
