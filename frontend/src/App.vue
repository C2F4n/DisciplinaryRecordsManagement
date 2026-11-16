<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { accountSection, registrationSections, systemSections } from './adminSections'
import { api, ApiError, errorText } from './api'
import type { Viewer } from './types'
import { roleNames } from './types'

const router = useRouter()
const route = useRoute()
const viewer = ref<Viewer | null>(null)
const checking = ref(true)
const problem = ref('')
const unreadCount = ref(0)
const registrationExpanded = ref(false)
const registrationActive = computed(() => route.name === 'admin' &&
  registrationSections.some(section => section.key === route.params.section))
const adminPageTitle = computed(() => {
  if (route.name === 'admin') return registrationActive.value ? '信息登记' : accountSection.label
})
const systemExpanded = ref(false)
const informationExpanded = ref(false)
const informationPages = [
  { name: 'permission-guide', label: '权限说明' },
  { name: 'approval-permission-guide', label: '审批权限' },
]
const UNREAD_REFRESH_MS = 30_000
let unreadTimer: number | undefined
function roleLabel() {
  const user = viewer.value
  if (user?.coreAdmin) return '核心管理员'
  const labels = new Set(user?.grants.map(g => roleNames[g.roleCode] || g.roleCode))
  if (user?.student) return ['学生', ...labels].join(' · ')
  return [...labels].join(' · ') || '普通账户'
}

function isTeacher(user: Viewer) {
  return user.grants.some(grant => grant.roleCode === 'TEACHER')
}

function destination(user: Viewer, name: typeof route.name) {
  if (user.passwordChangeRequired) return name === 'password' ? undefined : '/password'
  const teacher = isTeacher(user)
  const fallback = teacher ? '/' : user.canExportReports && !user.canViewRecords ? '/reports' : '/records'
  if (name === 'login' || (name === 'home' && !teacher) || (route.meta.coreAdmin && !user.coreAdmin) ||
      ((name === 'permission-requests' || name === 'notification-email') && !user.student) || (name === 'permission-review' && !user.canReviewPermissionRequests) ||
      (name === 'reports' && !user.canExportReports) ||
      (name === 'records' && !user.canViewRecords && user.canExportReports)) return fallback
}

async function refreshUnread() {
  const current = viewer.value
  if (!current || current.passwordChangeRequired) return
  try {
    const result = await api<{ count: number }>('/api/notices/unread')
    if (viewer.value === current) unreadCount.value = result.count
  } catch { /* The inbox shows its own error and the next poll can recover the count. */ }
}

async function refreshSession() {
  const current = viewer.value
  if (!current || current.passwordChangeRequired) return
  try {
    const updated = await api<Viewer>('/api/me')
    if (viewer.value !== current) return
    viewer.value = updated
    const redirect = destination(updated, route.name)
    if (redirect) await router.replace(redirect)
    void refreshUnread()
  } catch { /* Session expiration is handled by the API client. */ }
}

async function loadUser() {
  checking.value = true
  problem.value = ''
  try {
    viewer.value = await api<Viewer>('/api/me')
    const redirect = destination(viewer.value, route.name)
    if (redirect) await router.replace(redirect)
    void refreshUnread()
  } catch (e) {
    viewer.value = null
    if (e instanceof ApiError && e.status === 401) await router.replace('/login')
    else problem.value = errorText(e)
  } finally {
    checking.value = false
  }
}
async function expire() {
  viewer.value = null
  unreadCount.value = 0
  await router.replace('/login')
}
async function logout() {
  try {
    await api<void>('/api/logout', 'POST')
    await expire()
  } catch (e) { problem.value = errorText(e) }
}
watch(() => route.name, async (name) => {
  if (checking.value) return
  if (!viewer.value && name !== 'login') await router.replace('/login')
  else if (viewer.value) {
    const redirect = destination(viewer.value, name)
    if (redirect) await router.replace(redirect)
  }
})
watch([() => route.name, () => route.params.section], () => {
  if (registrationActive.value) registrationExpanded.value = true
}, { immediate: true })
watch(() => route.name, name => {
  if (name === 'system-management') systemExpanded.value = true
  if (informationPages.some(page => page.name === name)) informationExpanded.value = true
}, { immediate: true })
onMounted(async () => {
  window.addEventListener('session-expired', expire)
  window.addEventListener('notices-changed', refreshUnread)
  unreadTimer = window.setInterval(refreshSession, UNREAD_REFRESH_MS)
  await router.isReady()
  await loadUser()
})
onUnmounted(() => {
  window.removeEventListener('session-expired', expire)
  window.removeEventListener('notices-changed', refreshUnread)
  if (unreadTimer !== undefined) window.clearInterval(unreadTimer)
})
</script>

<template>
  <main v-if="checking || (!viewer && problem)" class="initial-screen" aria-live="polite">
    <img src="/favicon.svg" alt="" width="48" height="48">
    <p>{{ problem || '正在连接工作台…' }}</p>
    <button v-if="problem" class="primary" @click="loadUser">重试</button>
  </main>
  <RouterView v-else-if="!viewer && route.name === 'login'" @signed-in="loadUser" />
  <div v-else-if="viewer" class="workspace">
    <aside v-if="!viewer.passwordChangeRequired" class="sidebar">
      <div class="brand">
        <img src="/favicon.svg" alt="" width="42" height="42">
        <div><strong>自律会工作台</strong><small>机电工程学院</small></div>
      </div>
      <p class="nav-caption">业务工作台</p>
      <nav aria-label="工作台">
        <RouterLink v-if="isTeacher(viewer)" to="/"><span aria-hidden="true">⌂</span> 老师首页</RouterLink>
        <RouterLink v-if="viewer.canViewRecords || !viewer.canExportReports" to="/records"><span aria-hidden="true">▤</span> 违纪记录</RouterLink>
        <RouterLink v-if="viewer.canExportReports" to="/reports"><span aria-hidden="true">▧</span> 月报</RouterLink>
        <RouterLink to="/notices"><span aria-hidden="true">◉</span> 通知
          <strong v-if="unreadCount" class="notice-badge" :aria-label="unreadCount + ' 条未读'">{{ unreadCount > 99 ? '99+' : unreadCount }}</strong>
        </RouterLink>
        <RouterLink v-if="viewer.student" to="/permission-requests"><span aria-hidden="true">＋</span> 权限申请</RouterLink>
        <RouterLink v-if="viewer.canReviewPermissionRequests" to="/permission-review"><span aria-hidden="true">✓</span> 请求待处理</RouterLink>
        <div v-if="viewer.coreAdmin" class="sidebar-group" :class="{ expanded: registrationExpanded }">
          <button type="button" class="sidebar-toggle" :class="{ current: registrationActive }"
            :aria-expanded="registrationExpanded" aria-controls="registration-submenu"
            @click="registrationExpanded = !registrationExpanded">
            <span aria-hidden="true">☷</span> 信息登记 <span class="sidebar-chevron" aria-hidden="true"></span>
          </button>
          <div id="registration-submenu" v-show="registrationExpanded" class="sidebar-submenu">
            <RouterLink v-for="item in registrationSections" :key="item.key"
              :to="{ name: 'admin', params: { section: item.key } }">{{ item.label }}</RouterLink>
          </div>
        </div>
        <RouterLink v-if="viewer.coreAdmin" :to="{ name: 'admin', params: { section: accountSection.key } }">
          <span aria-hidden="true">♙</span> {{ accountSection.label }}
        </RouterLink>
        <div v-if="viewer.coreAdmin" class="sidebar-group" :class="{ expanded: systemExpanded }">
          <button type="button" class="sidebar-toggle" :class="{ current: route.name === 'system-management' }"
            :aria-expanded="systemExpanded" aria-controls="system-submenu" @click="systemExpanded = !systemExpanded">
            <span aria-hidden="true">⚙</span> 系统管理 <span class="sidebar-chevron" aria-hidden="true"></span>
          </button>
          <div id="system-submenu" v-show="systemExpanded" class="sidebar-submenu">
            <RouterLink v-for="item in systemSections" :key="item.key"
              :to="{ name: 'system-management', params: { section: item.key } }">{{ item.label }}</RouterLink>
          </div>
        </div>
        <div class="sidebar-group" :class="{ expanded: informationExpanded }">
          <button type="button" class="sidebar-toggle" :class="{ current: informationPages.some(page => page.name === route.name) }"
            :aria-expanded="informationExpanded" aria-controls="information-submenu"
            @click="informationExpanded = !informationExpanded">
            <span aria-hidden="true">ⓘ</span> 信息说明 <span class="sidebar-chevron" aria-hidden="true"></span>
          </button>
          <div id="information-submenu" v-show="informationExpanded" class="sidebar-submenu">
            <RouterLink v-for="page in informationPages" :key="page.name" :to="{ name: page.name }">{{ page.label }}</RouterLink>
          </div>
        </div>
      </nav>
      <p class="sidebar-note">山东建筑大学<br>机电工程学院自律会</p>
    </aside>
    <div class="workspace-body" :class="{ 'password-required': viewer.passwordChangeRequired }">
      <header class="topbar">
        <div class="breadcrumb">工作台 <span>/</span> {{ adminPageTitle || route.meta.title || (route.name === 'password' ? '修改密码' : route.name === 'home' ? '老师首页' : route.name === 'reports' ? '月报' : route.name === 'notices' ? '通知' : '违纪记录') }}</div>
        <div class="identity">
          <div class="avatar" aria-hidden="true">{{ viewer.displayName.slice(0, 1).toUpperCase() }}</div>
          <div class="identity-text"><strong>{{ viewer.displayName }}</strong><small>{{ roleLabel() }}</small></div>
          <RouterLink v-if="!viewer.passwordChangeRequired" class="quiet action-link" to="/password">修改密码</RouterLink>
          <button class="quiet" @click="logout">退出</button>
        </div>
      </header>
      <main class="content">
        <p v-if="problem" class="error" role="alert">{{ problem }}</p>
        <RouterView v-if="route.name !== 'login' && (!viewer.passwordChangeRequired || route.name === 'password') && (!route.meta.coreAdmin || viewer.coreAdmin)"
          :key="route.name" :viewer="viewer" v-on="route.name === 'password' ? { passwordChanged: expire } : {}" />
      </main>
    </div>
  </div>
</template>
