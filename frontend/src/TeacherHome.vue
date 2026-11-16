<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, downloadWorkbook, errorText } from './api'
import StatisticsPanel from './StatisticsPanel.vue'
import type { MonthlySummary, Viewer } from './types'

defineProps<{ viewer: Viewer }>()
const statisticsQuery = ref('status=APPROVED')
const latestReport = ref<MonthlySummary>()
const loading = ref(false)
const downloading = ref(false)
const loadError = ref('')
const downloadError = ref('')
const month = (report: MonthlySummary) => report.monthStart.slice(0, 7)

async function loadLatest() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await api<{ items: MonthlySummary[] }>('/api/reports?size=1')
    latestReport.value = page.items[0]
  } catch (e) { loadError.value = errorText(e) }
  finally { loading.value = false }
}

async function download(report: MonthlySummary) {
  downloading.value = true
  downloadError.value = ''
  try {
    await downloadWorkbook('/api/reports/' + report.id + '/download', month(report) + ' 违纪统计月报.xlsx')
  } catch (e) { downloadError.value = errorText(e) }
  finally { downloading.value = false }
}

onMounted(loadLatest)
</script>

<template>
  <div class="page-header">
    <h1>团委老师工作台</h1>
    <RouterLink class="secondary action-link" to="/records">进入记录管理</RouterLink>
  </div>

  <section class="panel teacher-report" aria-labelledby="latest-report">
    <div class="panel-heading">
      <h2 id="latest-report">最新月报</h2>
      <RouterLink class="text-button" to="/reports">全部月报</RouterLink>
    </div>
    <p v-if="loadError" class="error panel-message" role="alert">{{ loadError }}
      <button class="text-button" @click="loadLatest">重试</button>
    </p>
    <div v-else-if="loading" class="empty">正在加载月报…</div>
    <div v-else-if="!latestReport" class="empty">暂无已生成月报</div>
    <div v-else class="teacher-report-body">
      <div>
        <strong>{{ month(latestReport) }} 月报</strong>
        <span class="cell-sub">{{ latestReport.recordCount }} 条已归档记录</span>
      </div>
      <div class="buttons">
        <RouterLink class="secondary action-link" :to="{ name: 'reports', query: { report: latestReport.id } }">查看明细</RouterLink>
        <button class="secondary" :disabled="downloading" @click="download(latestReport)">
          {{ downloading ? '正在下载…' : '下载 Excel' }}
        </button>
      </div>
    </div>
    <p v-if="downloadError" class="error panel-message" role="alert">{{ downloadError }}</p>
  </section>

  <div class="teacher-stat-filter">
    <label>统计范围<select v-model="statisticsQuery">
      <option value="status=APPROVED">已审核 · 待处理</option>
      <option value="">全部正式记录</option>
    </select></label>
  </div>
  <StatisticsPanel :query="statisticsQuery" :revision="0" :pending-only="false" />
</template>
