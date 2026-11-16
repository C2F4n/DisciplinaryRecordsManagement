<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api, errorText } from './api'
import { roleNames } from './types'
import type { Dept, Metadata } from './types'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ review?: boolean }>()
type Status = 'PENDING' | 'APPROVED' | 'REJECTED'
type Entry = {
  id: number; requesterName: string; requesterNumber: string; roleCode: string; scopeLabel: string
  reason: string; status: Status; submittedAt: number; reviewerName: string | null
  decidedAt: number | null; decisionNote: string | null
}

const statuses: Record<Status, string> = { PENDING: '待处理', APPROVED: '已批准', REJECTED: '已驳回' }
const availableRoles = ['CLASS_COMMITTEE', 'OFFICER', 'DEPARTMENT_HEAD', 'DISCIPLINE_OFFICE', 'QUALITY_HEAD', 'PRESIDENT']
const maxNoteLength = 250
const endpoint = '/api/permission-requests'
const size = 8
const formatTime = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Shanghai' })
const departments = ref<Dept[]>([])
const form = reactive({ role: '', scope: '', reason: '' })
const scoped = computed(() => form.role === 'OFFICER' || form.role === 'DEPARTMENT_HEAD')
const items = ref<Entry[]>([])
const total = ref(0)
const page = ref(1)
const status = ref<Status>('PENDING')
const last = computed(() => Math.max(1, Math.ceil(total.value / size)))
const busy = ref(false)
const loading = ref(false)
const problem = ref('')
const formProblem = ref('')
const success = ref('')

async function load(target: number) {
  loading.value = true
  problem.value = ''
  try {
    const query = new URLSearchParams({ page: String(target), size: String(size) })
    if (props.review) query.set('status', status.value)
    const path = endpoint + (props.review ? '/queue?' : '/mine?') + query
    const result = await api<{ items: Entry[]; total: number }>(path)
    if (!result.items.length && result.total > 0 && target > 1) {
      await load(Math.ceil(result.total / size))
      return
    }
    items.value = result.items
    total.value = result.total
    page.value = target
  } catch (e) {
    items.value = []
    total.value = 0
    problem.value = errorText(e)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (busy.value || !form.role || !form.reason.trim() || (scoped.value && !form.scope)) return
  busy.value = true
  formProblem.value = ''
  success.value = ''
  try {
    await api(endpoint, 'POST', { role: form.role, scope: scoped.value ? form.scope : '', reason: form.reason.trim() })
    Object.assign(form, { role: '', scope: '', reason: '' })
    success.value = '申请已提交，审批结果会通过站内通知发送。'
    await load(1)
  } catch (e) { formProblem.value = errorText(e) }
  finally { busy.value = false }
}

async function decide(item: Entry, approve: boolean) {
  if (busy.value || loading.value) return
  const label = approve ? '批准' : '驳回'
  let note: string
  busy.value = true
  success.value = ''
  formProblem.value = ''
  try {
    try {
      const result = await ElMessageBox.prompt(
        item.requesterName + '申请「' + roleLabel(item) + '」。可填写处理意见。',
        label + '权限申请',
        { confirmButtonText: label, cancelButtonText: '取消', inputType: 'textarea',
          inputValidator: value => !value || value.length <= maxNoteLength || '处理意见最多 250 字' },
      )
      note = result.value || ''
    } catch { return }
    await api(endpoint + '/' + item.id + '/decision', 'PUT', { approve, note })
    success.value = '已' + label + '申请。'
    await load(page.value)
  } catch (e) { formProblem.value = errorText(e) }
  finally { busy.value = false }
}

function roleLabel(item: Entry) {
  return (roleNames[item.roleCode] || item.roleCode) + (item.scopeLabel ? ' · ' + item.scopeLabel : '')
}

onMounted(() => {
  void load(1)
  if (!props.review) {
    void api<Pick<Metadata, 'departments'>>('/api/metadata')
      .then(value => { departments.value = value.departments })
      .catch(e => { formProblem.value = errorText(e) })
  }
})
</script>

<template>
  <div class="page-header">
    <div><h1>{{ review ? '请求待处理' : '权限申请' }}</h1></div>
    <button class="secondary" :disabled="busy || loading" @click="load(page)">刷新</button>
  </div>

  <section v-if="!review" class="panel permission-form">
    <h2>提交申请</h2>
    <form class="fields" @submit.prevent="submit">
      <div class="two-col">
        <label>申请身份
          <select v-model="form.role" required :disabled="busy" @change="form.scope = ''">
            <option value="" disabled>请选择身份</option>
            <option v-for="role in availableRoles" :key="role" :value="role">{{ roleNames[role] }}</option>
          </select>
        </label>
        <label v-if="scoped">所属部门
          <select v-model="form.scope" required :disabled="busy">
            <option value="" disabled>请选择部门</option>
            <option v-for="department in departments" :key="department.code" :value="department.code">{{ department.name }}</option>
          </select>
        </label>
        <p v-else-if="form.role === 'CLASS_COMMITTEE'" class="muted">班委权限对应你当前所在的班级。</p>
      </div>
      <label>申请理由
        <textarea v-model="form.reason" rows="3" required :maxlength="maxNoteLength" :disabled="busy"
          placeholder="例如已担任的职务"></textarea>
      </label>
      <button class="primary" :disabled="busy || !form.role || !form.reason.trim() || (scoped && !form.scope)">提交申请</button>
    </form>
  </section>

  <p v-if="formProblem" class="error" role="alert">{{ formProblem }}</p>
  <p v-if="success" class="success" role="status">{{ success }}</p>

  <section class="panel permission-list" :aria-busy="loading" aria-label="申请记录">
    <div class="panel-heading">
      <h2>{{ review ? '权限请求' : '我的申请' }}</h2>
      <label v-if="review" class="permission-filter">处理状态
        <select v-model="status" :disabled="busy || loading" @change="load(1)">
          <option v-for="(label, key) in statuses" :key="key" :value="key">{{ label }}</option>
        </select>
      </label>
    </div>
    <p v-if="problem" class="error panel-message" role="alert">{{ problem }}</p>
    <div v-else-if="loading" class="empty">正在加载申请…</div>
    <div v-else-if="!items.length" class="empty">{{ review ? '暂无' + statuses[status] + '的申请' : '暂无申请记录' }}</div>
    <ul v-else class="notice-list">
      <li v-for="item in items" :key="item.id">
        <div class="notice-copy permission-copy">
          <div class="notice-heading">
            <strong>{{ roleLabel(item) }}</strong>
            <span class="state" :class="{ approved: item.status === 'APPROVED', pending: item.status === 'PENDING' }">{{ statuses[item.status] }}</span>
          </div>
          <small class="muted"><template v-if="review">{{ item.requesterName }} · {{ item.requesterNumber }} · </template>{{ formatTime.format(item.submittedAt) }} 提交</small>
          <p class="permission-reason">{{ item.reason }}</p>
          <template v-if="item.decidedAt != null">
            <p>处理人 {{ item.reviewerName }} · {{ formatTime.format(item.decidedAt) }} {{ item.status === 'APPROVED' ? '批准' : '驳回' }}</p>
            <p v-if="item.decisionNote" class="permission-reason">处理意见 {{ item.decisionNote }}</p>
          </template>
        </div>
        <div v-if="review && item.status === 'PENDING'" class="buttons">
          <button class="primary" :disabled="busy" @click="decide(item, true)">批准</button>
          <button class="danger" :disabled="busy" @click="decide(item, false)">驳回</button>
        </div>
      </li>
    </ul>
    <div v-if="last > 1" class="pager">
      <button class="secondary" :disabled="page <= 1 || busy || loading" @click="load(page - 1)">上一页</button>
      <span>{{ page }} / {{ last }}</span>
      <button class="secondary" :disabled="page >= last || busy || loading" @click="load(page + 1)">下一页</button>
    </div>
  </section>
</template>
