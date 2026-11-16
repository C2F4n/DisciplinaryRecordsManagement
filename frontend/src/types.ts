export type Grant = { roleCode: string; scopeKey: string }
export type Viewer = {
  id: number
  username: string
  displayName: string
  coreAdmin: boolean
  student: boolean
  canReviewPermissionRequests: boolean
  grants: Grant[]
  allRecords: boolean
  canExportReports: boolean
  canViewRecords: boolean
  canViewStatistics: boolean
  passwordChangeRequired: boolean
  officerDepartments: string[]
}
export const maxEmailLength = 254
export type Group = { id: number; name: string; counselor?: string }
export type ManagedClass = Group & { counselor: string; counselors: string[]; version: number; studentCount: number }
export type Counselor = { id: number; name: string }
export const maxClassCounselors = 3
export type RosterStudent = {
  id: number; studentNo: string; name: string; classId: number; className: string
  counselor: string; phone: string | null; validUntil: string; bound: boolean; version: number
}
export type StudentNumberRule = { minLength: number; maxLength: number; prefix: string; version: number }
export const studentNumberPattern = '[0-9]{1,32}'
export type Dept = { code: string; name: string }
export type ViolationType = Dept & { departmentCode: string }
export type Resolution = 'HANDED_BACK' | 'APPEALED'
export type WorkflowChoice = { code: string; label: string; resolution: Resolution | null; category: string | null }
export type Metadata = {
  today: string; departments: Dept[]; types: ViolationType[]; defaultDisposition: string
  inspectionOptions: WorkflowChoice[]; appealOptions: WorkflowChoice[]
}
export type Student = { id: number; studentNo: string; name: string; className: string; counselor: string }
export type State = 'PENDING' | 'APPROVED' | 'HANDED_BACK' | 'APPEALED' | 'RECORDED'
export type Entry = {
  id: number; studentNo: string; studentName: string; classId: number; className: string; counselor: string
  occurredOn: string; departmentCode: string; typeCode: string; reason: string; disposition: string
  inspectionScope: string; inspectionLabel: string; appealLabel: string | null
  remarks: string | null; status: State; rejectionReason: string | null
  version: number; editable: boolean; reviewable: boolean; resolvable: boolean; correctable: boolean
}
export type Page = { items: Entry[]; total: number }
export type Staff = { id: number; username: string; displayName: string; studentId: number | null; enabled: boolean }

export type MonthlySummary = { id: number; monthStart: string; generatedAt: number; recordCount: number }
export type MonthlyItem = {
  sourceRecordId: number; occurredOn: string; studentNo: string; studentName: string; className: string; counselor: string
  departmentName: string; typeName: string; reason: string; disposition: string
  inspectionScope: string; inspectionLabel: string; remarks: string | null
}
export type MonthlyDetail = { report: MonthlySummary; entries: { items: MonthlyItem[]; total: number } }

export type Notice = {
  id: number; title: string; message: string; target: 'VIOLATION' | 'MONTHLY_REPORT' | 'PERMISSION_REVIEW' | 'PERMISSION_REQUEST'
  subjectId: number; createdAt: number; readAt: number | null
}

export type StatisticsPeriod = 'DAY' | 'WEEK' | 'MONTH'
export type StatisticsBucket = { key: string; label: string; count: number }
export type ViolationStatistics = {
  total: number
  byStatus: StatisticsBucket[]; byType: StatisticsBucket[]; byDepartment: StatisticsBucket[]; byClass: StatisticsBucket[]
  trend: { start: string; count: number }[]; trendLimited: boolean
}

export const stateNames: Record<State, string> = {
  PENDING: '待审核', APPROVED: '已审核', HANDED_BACK: '已交回', APPEALED: '已申诉', RECORDED: '已录入',
}
export const roleNames: Record<string, string> = {
  PRESIDENT: '主席', TEACHER: '团委老师', QUALITY_HEAD: '素拓中心部长', DISCIPLINE_OFFICE: '团委办公室核对',
  OFFICER: '部门干事', DEPARTMENT_HEAD: '部门部长', CLASS_COMMITTEE: '班委',
}
