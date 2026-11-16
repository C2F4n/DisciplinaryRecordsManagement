<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, errorText } from './api'
import { stateNames } from './types'
import type { State } from './types'

type AuditEntry = {
  id: number; actor: string | null; action: string; target: string; targetId: number
  previousState: string | null; newState: string | null; details: string | null; requestId: string | null; createdAt: number
}

const actions: Record<string, string> = {
  BIND: '注册学生账号', CREATE_CLASS: '添加班级', CREATE_STUDENT: '登记学生', REISSUE_INVITE: '重发邀请码',
  UPDATE_NOTIFICATION_EMAIL: '修改通知邮箱', UPDATE_SMTP_SETTINGS: '修改 SMTP 配置',
  CHANGE_SMTP_SETTINGS_PASSWORD: '修改 SMTP 配置密码',
  CREATE_STAFF: '创建员工账号', GRANT_ROLE: '分配身份', REVOKE_ROLE: '撤销身份',
  ENABLE_ACCOUNT: '启用账号', DISABLE_ACCOUNT: '停用账号', CHANGE_PASSWORD: '修改密码', RESET_PASSWORD: '重置密码',
  CREATE_RECORD: '登记违纪', UPDATE_RECORD: '修改违纪', DELETE_RECORD: '删除违纪',
  APPROVE: '审核通过', REJECT: '审核驳回', RESOLVE: '办公室处理', CORRECT_VIOLATION: '纠正违纪记录',
  GENERATE_MONTHLY_REPORT: '生成月报', DOWNLOAD_MONTHLY_REPORT: '下载月报',
  DOWNLOAD_FILTERED_VIOLATIONS: '导出正式记录', DOWNLOAD_BULLETIN: '导出通报表', PURGE_RETENTION_WEEK: '到期清理',
  UPDATE_CLASS: '修改班级', UPDATE_STUDENT_PROFILE: '修改学籍', REVOKE_STUDENT_COMMITTEE: '撤销原班委身份',
  DELETE_CLASS: '删除班级', DELETE_STUDENT: '删除学生名册',
  CREATE_COUNSELOR: '录入辅导员',
  REQUEST_PERMISSION: '提交权限申请', APPROVE_PERMISSION: '批准权限申请', REJECT_PERMISSION: '驳回权限申请',
  UPDATE_STUDENT_NUMBER_RULE: '修改学号规则', UPDATE_EXPORT_TEMPLATE: '修改导出模板',
  UPDATE_WORKFLOW_OPTION: '维护业务选项', UPDATE_TYPE_DEPARTMENT: '调整违纪类型归属',
}
const targets: Record<string, string> = {
  ACCOUNT: '账号', CLASS: '班级', STUDENT: '学生', VIOLATION: '违纪记录', MONTHLY_REPORT: '月报',
  RETENTION_WEEK: '清理批次', STUDENT_NUMBER_RULE: '学号规则', EXPORT_TEMPLATE: '导出模板',
  WORKFLOW_OPTION: '业务选项', VIOLATION_TYPE: '违纪类型', COUNSELOR: '辅导员', PERMISSION_REQUEST: '权限申请', SMTP_SETTINGS: 'SMTP 配置',
}
const filter = reactive({ from: '', to: '', actor: '', action: '', target: '', targetId: '' })
let applied = { ...filter }
const items = ref<AuditEntry[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const last = computed(() => Math.max(1, Math.ceil(total.value / size)))
const loading = ref(false)
const problem = ref('')
const date = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' })

function valueLabel(value: string | null) {
  if (!value) return '—'
  return stateNames[value as State] ?? (value === 'DELETED' ? '已删除' : value)
}

async function load(index = 1, criteria = applied) {
  if (loading.value) return
  loading.value = true
  problem.value = ''
  try {
    const params = new URLSearchParams({ page: String(index), size: String(size) })
    for (const [key, value] of Object.entries(criteria)) if (value) params.set(key, value)
    const data = await api<{ items: AuditEntry[]; total: number }>('/api/admin/audit?' + params)
    items.value = data.items
    total.value = data.total
    page.value = index
    applied = criteria
  } catch (e) { problem.value = errorText(e) }
  finally { loading.value = false }
}

onMounted(() => load())
</script>

<template>
  <form class="panel filters" @submit.prevent="load(1, { ...filter })">
    <label>开始日期<input v-model="filter.from" type="date"></label>
    <label>结束日期<input v-model="filter.to" type="date" :min="filter.from || undefined"></label>
    <label>操作账号<input v-model.trim="filter.actor" maxlength="48" placeholder="完整登录名"></label>
    <label>操作<select v-model="filter.action"><option value="">全部操作</option>
      <option v-for="(label, code) in actions" :key="code" :value="code">{{ label }}</option>
    </select></label>
    <label>对象<select v-model="filter.target"><option value="">全部对象</option>
      <option v-for="(label, code) in targets" :key="code" :value="code">{{ label }}</option>
    </select></label>
    <label>对象编号<input v-model.trim="filter.targetId" inputmode="numeric" pattern="[1-9][0-9]*" maxlength="19"></label>
    <div class="buttons"><button class="primary" :disabled="loading">查询</button></div>
  </form>
  <section class="panel table-panel" :aria-busy="loading" aria-labelledby="audit-heading">
    <div class="panel-heading"><h2 id="audit-heading">审计日志</h2><span class="muted">共 {{ total }} 条</span></div>
    <p v-if="problem" class="error panel-message" role="alert">{{ problem }}</p>
    <div v-if="loading && !items.length" class="empty">正在查询…</div>
    <div v-else-if="!items.length" class="empty">暂无符合条件的操作</div>
    <table v-else class="record-table">
      <thead><tr><th>时间</th><th>操作账号</th><th>操作</th><th>对象</th><th>变更</th></tr></thead>
      <tbody><tr v-for="item in items" :key="item.id">
        <td data-label="时间">{{ date.format(item.createdAt) }}</td>
        <td data-label="操作账号">{{ item.actor || '系统' }}</td>
        <td data-label="操作">{{ actions[item.action] || item.action }}</td>
        <td data-label="对象">{{ targets[item.target] || item.target }} · {{ item.targetId }}</td>
        <td data-label="变更">
          <span v-if="item.previousState || item.newState">{{ valueLabel(item.previousState) }} → {{ valueLabel(item.newState) }}</span>
          <details v-if="item.details || item.requestId">
            <summary>详情</summary>
            <p v-if="item.details" class="audit-details">{{ item.details }}</p>
            <small v-if="item.requestId" class="cell-sub">请求编号 {{ item.requestId }}</small>
          </details>
          <span v-else-if="!item.previousState && !item.newState">—</span>
        </td>
      </tr></tbody>
    </table>
    <div v-if="last > 1" class="pager">
      <button class="secondary" :disabled="loading || page <= 1" @click="load(page - 1)">上一页</button>
      <span>{{ page }} / {{ last }}</span>
      <button class="secondary" :disabled="loading || page >= last" @click="load(page + 1)">下一页</button>
    </div>
  </section>
</template>
