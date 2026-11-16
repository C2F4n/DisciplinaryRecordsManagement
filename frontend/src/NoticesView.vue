<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, errorText } from './api'
import type { Notice, Viewer } from './types'

defineProps<{ viewer: Viewer }>()
const router = useRouter()
const notices = ref<Notice[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const updating = ref(false)
const problem = ref('')
const lastPage = computed(() => Math.max(1, Math.ceil(total.value / size)))
const formatDate = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

async function load(target = 1) {
  loading.value = true
  problem.value = ''
  try {
    const response = await api<{ items: Notice[]; total: number }>('/api/notices?page=' + target + '&size=' + size)
    notices.value = response.items
    total.value = response.total
    page.value = target
  } catch (e) { problem.value = errorText(e) }
  finally { loading.value = false }
}

function changed() { window.dispatchEvent(new Event('notices-changed')) }

async function markAll() {
  updating.value = true
  problem.value = ''
  try {
    await api('/api/notices/read-all', 'PUT')
    changed()
    await load(page.value)
  } catch (e) { problem.value = errorText(e) }
  finally { updating.value = false }
}

async function open(notice: Notice) {
  updating.value = true
  problem.value = ''
  try {
    if (notice.readAt == null) {
      await api('/api/notices/' + notice.id + '/read', 'PUT')
      changed()
    }
    if (notice.target === 'MONTHLY_REPORT') {
      await router.push({ name: 'reports', query: { report: String(notice.subjectId) } })
    } else if (notice.target === 'PERMISSION_REVIEW') {
      await router.push({ name: 'permission-review' })
    } else if (notice.target === 'PERMISSION_REQUEST') {
      await router.push({ name: 'permission-requests' })
    } else {
      await router.push({ name: 'records', query: { record: String(notice.subjectId) } })
    }
  } catch (e) { problem.value = errorText(e) }
  finally { updating.value = false }
}

onMounted(() => load())
</script>

<template>
  <div class="page-header">
    <div><p class="eyebrow">工作提醒</p><h1>站内通知</h1><p class="muted">查看违纪状态、权限申请和待办提醒。</p></div>
    <div class="buttons">
      <RouterLink v-if="viewer.student" class="secondary action-link" :to="{ name: 'notification-email' }">通知邮箱</RouterLink>
      <button class="secondary" :disabled="loading || updating || !total" @click="markAll">全部标为已读</button>
    </div>
  </div>
  <section class="panel" aria-label="通知列表">
    <div class="panel-heading"><h2>全部通知</h2><small class="muted">共 {{ total }} 条</small></div>
    <p v-if="problem" class="error panel-message" role="alert">{{ problem }}</p>
    <div v-if="loading" class="empty">正在加载通知…</div>
    <div v-else-if="!notices.length && !problem" class="empty"><strong>暂无通知</strong><span>与你有关的记录状态变化会显示在这里。</span></div>
    <ul v-else class="notice-list">
      <li v-for="notice in notices" :key="notice.id" :class="{ unread: notice.readAt == null }">
        <div class="notice-copy">
          <div class="notice-heading"><strong>{{ notice.title }}</strong><span v-if="notice.readAt == null" class="state approved">未读</span></div>
          <p>{{ notice.message }}</p>
          <small class="muted">{{ formatDate.format(notice.createdAt) }}</small>
        </div>
        <button class="text-button" :disabled="updating" @click="open(notice)">{{ notice.target === 'MONTHLY_REPORT' ? '查看月报' : notice.target === 'VIOLATION' ? '查看记录' : '查看申请' }}</button>
      </li>
    </ul>
    <div v-if="lastPage > 1" class="pager">
      <button class="secondary" :disabled="page <= 1 || loading || updating" @click="load(page - 1)">上一页</button>
      <span>{{ page }} / {{ lastPage }}</span>
      <button class="secondary" :disabled="page >= lastPage || loading || updating" @click="load(page + 1)">下一页</button>
    </div>
  </section>
</template>
