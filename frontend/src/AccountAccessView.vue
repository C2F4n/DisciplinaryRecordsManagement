<script setup lang="ts">
import { onActivated, onDeactivated, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorText } from './api'
import { maxPasswordLength, minPasswordLength, passwordPattern, passwordRule } from './passwordPolicy'
import { roleNames } from './types'
import type { Dept, Grant, Group, Metadata, Staff, Viewer } from './types'

defineProps<{ viewer: Viewer }>()
type TemporaryPassword = { password: string; expiresAt: number }
const groups = ref<Group[]>([])
const departments = ref<Dept[]>([])
const busy = ref(false)
const problem = ref('')
const temporary = ref<TemporaryPassword>()
const staff = reactive({ username: '', password: '' })
const query = ref('')
const account = ref<Staff>()
const grants = ref<Grant[]>([])
const searched = ref(false)
const role = ref('OFFICER')
const scope = ref('')
let active = false
const expiryFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

async function run(task: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  problem.value = ''
  try { await task() }
  catch (e) { problem.value = errorText(e) }
  finally { busy.value = false }
}

onActivated(() => {
  active = true
  void run(async () => {
    const [metadata, classes] = await Promise.all([
      api<Pick<Metadata, 'departments'>>('/api/metadata'),
      api<Group[]>('/api/admin/classes'),
    ])
    departments.value = metadata.departments
    groups.value = classes
  })
})
onDeactivated(() => {
  active = false
  staff.password = ''
  temporary.value = undefined
})

async function findAccount() {
  temporary.value = undefined
  searched.value = false
  account.value = undefined
  grants.value = []
  const users = await api<Staff[]>('/api/admin/accounts?' + new URLSearchParams({ username: query.value }))
  account.value = users[0]
  if (account.value) grants.value = await api<Grant[]>('/api/admin/accounts/' + account.value.id + '/roles')
  searched.value = true
}
const addStaff = () => run(async () => {
  await api('/api/admin/accounts', 'POST', staff)
  query.value = staff.username
  staff.username = staff.password = ''
  await findAccount()
  ElMessage.success('已添加员工账号')
})
const changeGrant = (grant?: Grant) => run(async () => {
  if (!account.value) return
  await api('/api/admin/accounts/' + account.value.id + '/roles', grant ? 'DELETE' : 'POST',
    grant ? { role: grant.roleCode, scope: grant.scopeKey } : { role: role.value, scope: scope.value })
  await findAccount()
  ElMessage.success(grant ? '已撤销身份' : '已分配身份')
})
async function toggle() {
  const current = account.value
  if (!current) return
  try {
    await ElMessageBox.confirm(current.enabled ? '停用后该账号将不能访问系统。' : '确认恢复此账号？',
      current.enabled ? '停用账号' : '启用账号', { confirmButtonText: '确认', cancelButtonText: '取消' })
  } catch { return }
  await run(async () => {
    await api('/api/admin/accounts/' + current.id + '/enabled', 'PUT', { enabled: !current.enabled })
    await findAccount()
  })
}
async function resetPassword() {
  const target = account.value
  if (!target || busy.value) return
  try {
    await ElMessageBox.confirm('原密码和已登录会话将立即失效。', '重置 ' + target.username + ' 的密码',
      { confirmButtonText: '生成临时密码', cancelButtonText: '取消' })
  } catch { return }
  await run(async () => {
    temporary.value = undefined
    const result = await api<TemporaryPassword>('/api/admin/accounts/' + target.id + '/password-reset', 'POST')
    if (active && account.value?.id === target.id) temporary.value = result
  })
}
const scopeLabel = (key: string) => departments.value.find(d => d.code === key)?.name
  || groups.value.find(c => String(c.id) === key)?.name || key
</script>

<template>
  <p v-if="problem" class="error" role="alert">{{ problem }}</p>
  <section class="panel" aria-labelledby="create-staff-title">
    <div class="panel-heading"><h2 id="create-staff-title">创建员工账号</h2></div>
    <form class="fields management-form" @submit.prevent="addStaff">
      <p class="muted">学生凭名册信息自行注册。老师等管理人员在这里开户。</p>
      <div class="two-col">
        <label>登录名<input v-model.trim="staff.username" placeholder="至少 4 位，小写字母开头"
          pattern="[a-z][a-z0-9_.\-]{3,47}" required autocomplete="off"></label>
        <label>密码
          <input v-model="staff.password" type="password" :minlength="minPasswordLength" :maxlength="maxPasswordLength"
            :pattern="passwordPattern" :title="passwordRule" required autocomplete="new-password">
          <small class="muted">{{ passwordRule }}</small>
        </label>
      </div>
      <button class="primary" :disabled="busy">创建账号</button>
    </form>
  </section>

  <section class="panel" aria-labelledby="find-account-title">
    <div class="panel-heading"><h2 id="find-account-title">查找账号</h2></div>
    <div class="management-form">
      <form class="fields" @submit.prevent="run(findAccount)">
        <label>登录名或学号
          <div class="input-group"><input v-model.trim="query" maxlength="48" required>
            <button class="secondary" :disabled="busy">查找</button>
          </div>
        </label>
      </form>
      <p v-if="searched && !account" class="panel-feedback muted">未找到该账号。</p>
      <div v-if="account" class="panel-feedback candidate">
        <strong>{{ account.displayName }} · {{ account.username }}</strong>
        <span>{{ account.enabled ? '启用' : '停用' }}</span>
      </div>
    </div>
  </section>

  <template v-if="account && account.id !== viewer.id">
    <section class="panel" aria-labelledby="assign-role-title">
      <div class="panel-heading"><h2 id="assign-role-title">分配身份</h2></div>
      <div class="management-form">
        <p v-if="!grants.length" class="muted">该账号暂无已分配身份。</p>
        <div v-else class="class-list">
          <div v-for="g in grants" :key="g.roleCode + ':' + g.scopeKey">
            <span>{{ roleNames[g.roleCode] }} {{ scopeLabel(g.scopeKey) }}</span>
            <button class="text-button" :disabled="busy" @click="changeGrant(g)">撤销</button>
          </div>
        </div>
        <form class="fields nested-form" @submit.prevent="changeGrant()">
          <div class="two-col">
            <label>身份<select v-model="role" @change="scope = ''"><option v-for="(label, code) in roleNames" :key="code" :value="code">{{ label }}</option></select></label>
            <label v-if="role === 'OFFICER' || role === 'DEPARTMENT_HEAD'">部门<select v-model="scope" required><option value="">请选择</option><option v-for="d in departments" :key="d.code" :value="d.code">{{ d.name }}</option></select></label>
            <label v-if="role === 'CLASS_COMMITTEE'">所在班级<select v-model="scope" required><option value="">请选择</option><option v-for="c in groups" :key="c.id" :value="String(c.id)">{{ c.name }}</option></select></label>
          </div>
          <button class="primary" :disabled="busy">分配</button>
        </form>
      </div>
    </section>

    <section class="panel" aria-labelledby="account-security-title">
      <div class="panel-heading"><h2 id="account-security-title">账号状态与密码</h2></div>
      <div class="management-form">
        <div class="buttons">
          <button type="button" class="secondary" :disabled="busy" @click="resetPassword">重置密码</button>
          <button type="button" class="secondary" :disabled="busy" @click="toggle">{{ account.enabled ? '停用账号' : '启用账号' }}</button>
        </div>
        <div v-if="temporary" class="panel-feedback candidate temporary-password" role="status">
          <strong>临时密码 <code>{{ temporary.password }}</code></strong>
          <span>有效期至 {{ expiryFormat.format(temporary.expiresAt) }}。请核实身份后单独交付。</span>
          <button class="text-button" @click="temporary = undefined">收起</button>
        </div>
      </div>
    </section>
  </template>
</template>
