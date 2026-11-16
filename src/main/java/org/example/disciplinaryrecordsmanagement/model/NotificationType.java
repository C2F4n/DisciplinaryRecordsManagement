package org.example.disciplinaryrecordsmanagement.model;

public enum NotificationType {
    VIOLATION_REGISTERED(Target.VIOLATION, "新增违纪登记", "有一条记录已登记，请查看详情和当前状态。"),
    VIOLATION_APPROVED(Target.VIOLATION, "违纪记录已审核", "你的违纪记录已审核通过，请查看处理意见。"),
    VIOLATION_REJECTED(Target.VIOLATION, "登记已被驳回", "你登记的一条记录已被退回，请查看驳回原因。"),
    VIOLATION_HANDED_BACK(Target.VIOLATION, "违纪记录已交回", "你的违纪记录已标记为已交回。"),
    VIOLATION_APPEALED(Target.VIOLATION, "违纪记录已申诉", "你的违纪记录已标记为已申诉。"),
    VIOLATION_CORRECTED(Target.VIOLATION, "违纪记录已更正", "管理员更正了一条与你有关的记录，请查看最新详情。"),
    REVIEW_OVERDUE(Target.VIOLATION, "有记录待审核", "本部门有一条记录在录入次日仍未审核，请及时处理。"),
    REPORT_PENDING_REVIEW(Target.MONTHLY_REPORT, "月报生成时仍有待审记录", "月报生成时有 %d 条尚未审核的记录没有入报。"),
    PERMISSION_REQUESTED(Target.PERMISSION_REVIEW, "有新的权限申请", "请在请求待处理中查看申请详情。"),
    PERMISSION_APPROVED(Target.PERMISSION_REQUEST, "权限申请已批准", "申请的身份已生效，可查看审批记录。"),
    PERMISSION_REJECTED(Target.PERMISSION_REQUEST, "权限申请未通过", "可在权限申请中查看处理结果。");

    public enum Target { VIOLATION, MONTHLY_REPORT, PERMISSION_REVIEW, PERMISSION_REQUEST }

    private final Target target;
    private final String title;
    private final String message;

    NotificationType(Target target, String title, String message) {
        this.target = target;
        this.title = title;
        this.message = message;
    }

    public Target target() { return target; }
    public String title() { return title; }
    public String message(Integer count) { return count == null ? message : message.formatted(count); }
}
