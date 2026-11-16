export const registrationSections = [
  { key: 'students', label: '基础信息写入' },
  { key: 'classes', label: '班级信息写入' },
  { key: 'edit', label: '信息修改' },
] as const

export const accountSection = { key: 'accounts', label: '账号注册与权限分配' } as const
export const adminSections = [...registrationSections, accountSection]

export const systemSections = [
  { key: 'inspections', label: '检查项目' },
  { key: 'appeals', label: '申诉管理' },
  { key: 'types', label: '违纪类型归属' },
  { key: 'templates', label: '导出模板' },
  { key: 'retention', label: '数据保留' },
  { key: 'audit', label: '审计日志' },
  { key: 'smtp', label: 'SMTP 配置' },
] as const
