<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, errorText } from './api'

type Week = { weekStart: string; weekEnd: string; expiresOn: string; purgeable: number; deferred: number }
type Run = { id: number; weekStart: string; weekEnd: string; executedAt: number; removedRecords: number }
type Page<T> = { items: T[]; total: number }
type Overview = { asOf: string; enabled: boolean; years: number; purgeable: number; deferred: number; weeks: Page<Week> }

const overview = ref<Overview>()
const history = ref<Page<Run>>({ items: [], total: 0 })
const previewPage = ref(1)
const historyPage = ref(1)
const pageSize = 10
const previewLoading = ref(false)
const historyLoading = ref(false)
const previewError = ref('')
const historyError = ref('')
const previewPages = computed(() => Math.max(1, Math.ceil((overview.value?.weeks.total ?? 0) / pageSize)))
const historyPages = computed(() => Math.max(1, Math.ceil(history.value.total / pageSize)))
const formatDate = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

async function loadPreview(page = 1) {
  previewLoading.value = true
  previewError.value = ''
  try {
    overview.value = await api<Overview>('/api/admin/retention?page=' + page + '&size=' + pageSize)
    if (page > previewPages.value) await loadPreview(previewPages.value)
    else previewPage.value = page
  } catch (e) { previewError.value = errorText(e) }
  finally { previewLoading.value = false }
}

async function loadHistory(page = 1) {
  historyLoading.value = true
  historyError.value = ''
  try {
    history.value = await api<Page<Run>>('/api/admin/retention/history?page=' + page + '&size=' + pageSize)
    historyPage.value = page
  } catch (e) { historyError.value = errorText(e) }
  finally { historyLoading.value = false }
}

async function refresh() {
  await Promise.all([loadPreview(previewPage.value), loadHistory(historyPage.value)])
}

onMounted(refresh)
</script>

<template>
  <section class="panel retention-panel" aria-labelledby="retention-overview">
    <div class="panel-heading">
      <div><h2 id="retention-overview">到期清理预览</h2>
        <small class="muted">按发生日期归周，每周以周五为截止日。</small></div>
      <button class="secondary" :disabled="previewLoading || historyLoading" @click="refresh">刷新</button>
    </div>
    <p v-if="previewError" class="error panel-message" role="alert">{{ previewError }}</p>
    <div v-if="previewLoading && !overview" class="empty">正在计算到期记录…</div>
    <template v-if="overview">
      <div class="retention-overview">
        <p><span class="state" :class="overview.enabled ? 'recorded' : 'pending'">{{ overview.enabled ? '自动清理已启用' : '仅预览 · 自动清理未启用' }}</span>
          <span class="muted"> 截至 {{ overview.asOf }} · 保留 {{ overview.years }} 年</span></p>
        <div class="stat-summary">
          <div><strong>{{ overview.purgeable }}</strong><small>到期可清理</small></div>
          <div><strong>{{ overview.deferred }}</strong><small>到期但暂留</small></div>
          <div><strong>{{ overview.weeks.total }}</strong><small>涉及周数</small></div>
        </div>
        <p class="muted">已审核未处理的记录长期保留。已交回但尚未入月报的记录暂留；已冻结的月报和下载文件不受影响。</p>
      </div>
      <div v-if="!overview.weeks.items.length" class="empty"><strong>暂无到期记录</strong></div>
      <table v-else class="record-table">
        <thead><tr><th>业务周</th><th>到期日</th><th>可清理</th><th>待入月报</th></tr></thead>
        <tbody><tr v-for="week in overview.weeks.items" :key="week.weekStart">
          <td data-label="业务周">{{ week.weekStart }} 至 {{ week.weekEnd }}</td>
          <td data-label="到期日">{{ week.expiresOn }}</td>
          <td data-label="可清理">{{ week.purgeable }}</td>
          <td data-label="待入月报">{{ week.deferred }}</td>
        </tr></tbody>
      </table>
      <div v-if="previewPages > 1" class="pager">
        <button class="secondary" :disabled="previewPage <= 1 || previewLoading" @click="loadPreview(previewPage - 1)">上一页</button>
        <span>{{ previewPage }} / {{ previewPages }}</span>
        <button class="secondary" :disabled="previewPage >= previewPages || previewLoading" @click="loadPreview(previewPage + 1)">下一页</button>
      </div>
    </template>
  </section>

  <section class="panel retention-panel" aria-labelledby="retention-history">
    <div class="panel-heading"><div><h2 id="retention-history">清理历史</h2><small class="muted">共 {{ history.total }} 批</small></div></div>
    <p v-if="historyError" class="error panel-message" role="alert">{{ historyError }}</p>
    <div v-else-if="historyLoading" class="empty">正在加载清理历史…</div>
    <div v-else-if="!history.items.length" class="empty"><strong>尚无清理记录</strong></div>
    <table v-else class="record-table">
      <thead><tr><th>业务周</th><th>执行时间</th><th>清理数量</th></tr></thead>
      <tbody><tr v-for="run in history.items" :key="run.id">
        <td data-label="业务周">{{ run.weekStart }} 至 {{ run.weekEnd }}</td>
        <td data-label="执行时间">{{ formatDate.format(run.executedAt) }}</td>
        <td data-label="清理数量">{{ run.removedRecords }} 条</td>
      </tr></tbody>
    </table>
    <div v-if="historyPages > 1" class="pager">
      <button class="secondary" :disabled="historyPage <= 1 || historyLoading" @click="loadHistory(historyPage - 1)">上一页</button>
      <span>{{ historyPage }} / {{ historyPages }}</span>
      <button class="secondary" :disabled="historyPage >= historyPages || historyLoading" @click="loadHistory(historyPage + 1)">下一页</button>
    </div>
  </section>
</template>
