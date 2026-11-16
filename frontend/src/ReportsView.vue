<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, downloadWorkbook, errorText } from './api'
import type { MonthlyDetail, MonthlyItem, MonthlySummary, Viewer } from './types'

defineProps<{ viewer: Viewer }>()
const route = useRoute()
const reports = ref<MonthlySummary[]>([])
const selected = ref<MonthlySummary>()
const detail = ref<MonthlyDetail>()
const item = ref<MonthlyItem>()
const listLoading = ref(false)
const detailLoading = ref(false)
const downloading = ref(false)
const listError = ref('')
const detailError = ref('')
const listPage = ref(1)
const entryPage = ref(1)
const listTotal = ref(0)
const listSize = 12
const entrySize = 20
const listPages = computed(() => Math.max(1, Math.ceil(listTotal.value / listSize)))
const entryPages = computed(() => Math.max(1, Math.ceil((detail.value?.entries.total ?? 0) / entrySize)))
const month = (summary: MonthlySummary) => summary.monthStart.slice(0, 7)
const generatedAt = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

async function loadReports(page = 1) {
  listLoading.value = true
  listError.value = ''
  try {
    const data = await api<{ items: MonthlySummary[]; total: number }>('/api/reports?page=' + page + '&size=' + listSize)
    reports.value = data.items
    listTotal.value = data.total
    listPage.value = page
    if (!selected.value) {
      const id = route.query.report
      if (typeof id === 'string' && /^[1-9]\d*$/.test(id)) {
        const linked = await fetchReport(id, 1)
        selected.value = linked.report
        detail.value = linked
        entryPage.value = 1
      } else if (reports.value[0]) await selectReport(reports.value[0])
    }
  } catch (e) { listError.value = errorText(e) }
  finally { listLoading.value = false }
}

function fetchReport(id: number | string, page: number) {
  return api<MonthlyDetail>('/api/reports/' + id + '?page=' + page + '&size=' + entrySize)
}

async function selectReport(report: MonthlySummary, page = 1) {
  selected.value = report
  detail.value = undefined
  item.value = undefined
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await fetchReport(report.id, page)
    entryPage.value = page
  } catch (e) { detailError.value = errorText(e) }
  finally { detailLoading.value = false }
}

async function download() {
  if (!selected.value) return
  const report = selected.value
  downloading.value = true
  detailError.value = ''
  try {
    await downloadWorkbook('/api/reports/' + report.id + '/download', month(report) + ' 违纪统计月报.xlsx')
  } catch (e) { detailError.value = errorText(e) }
  finally { downloading.value = false }
}

onMounted(() => loadReports())
</script>

<template>
  <div class="page-header">
    <div><p class="eyebrow">统计与归档</p><h1>违纪统计月报</h1><p class="muted">按交回时间归月。月报生成后，明细和下载文件固定保留。</p></div>
  </div>
  <section class="panel report-index" aria-label="月报目录">
    <div class="panel-heading"><h2>已生成月报</h2><small class="muted">共 {{ listTotal }} 期</small></div>
    <p v-if="listError" class="error panel-message" role="alert">{{ listError }} <button class="text-button" @click="loadReports(listPage)">重试</button></p>
    <div v-else-if="listLoading && !reports.length" class="empty">正在加载月报…</div>
    <div v-else-if="!reports.length" class="empty"><strong>暂无已生成月报</strong><span>系统生成月报后会在这里归档。</span></div>
    <div v-else class="report-months">
      <button v-for="report in reports" :key="report.id" class="report-month"
        :class="{ active: selected?.id === report.id }" :aria-pressed="selected?.id === report.id"
        :disabled="detailLoading || listLoading" @click="selectReport(report)">
        <strong>{{ month(report) }}</strong><span>{{ report.recordCount }} 条</span>
      </button>
    </div>
    <div v-if="listPages > 1" class="pager">
      <button class="secondary" :disabled="listPage <= 1 || listLoading || detailLoading" @click="loadReports(listPage - 1)">上一页</button>
      <span>{{ listPage }} / {{ listPages }}</span>
      <button class="secondary" :disabled="listPage >= listPages || listLoading || detailLoading" @click="loadReports(listPage + 1)">下一页</button>
    </div>
  </section>

  <section v-if="selected" class="panel table-panel" aria-label="冻结月报明细">
    <div class="panel-heading report-detail-heading">
      <div><h2>{{ month(selected) }} 月报 <span class="state recorded">已冻结</span></h2>
        <small class="muted">共 {{ selected.recordCount }} 条 · 生成于 {{ generatedAt.format(selected.generatedAt) }}</small></div>
      <button class="primary" :disabled="downloading" @click="download">{{ downloading ? '正在下载…' : '下载 Excel' }}</button>
    </div>
    <p v-if="detailError" class="error panel-message" role="alert">{{ detailError }}</p>
    <div v-if="detailLoading" class="empty">正在加载冻结明细…</div>
    <div v-else-if="detail && !detail.entries.items.length" class="empty"><strong>本期没有入报记录</strong></div>
    <table v-else-if="detail" class="record-table">
      <thead><tr><th>发生日期</th><th>学生</th><th>班级</th><th>违纪类型</th><th>所属部门</th><th>明细</th></tr></thead>
      <tbody><tr v-for="r in detail.entries.items" :key="r.sourceRecordId">
        <td data-label="发生日期">{{ r.occurredOn }}</td>
        <td data-label="学生"><strong>{{ r.studentName }}</strong><small class="cell-sub">{{ r.studentNo }}</small></td>
        <td data-label="班级">{{ r.className }}</td>
        <td data-label="违纪类型">{{ r.typeName }}</td>
        <td data-label="所属部门">{{ r.departmentName }}</td>
        <td class="row-actions"><button class="text-button" @click="item = r">详情</button></td>
      </tr></tbody>
    </table>
    <div v-if="detail && entryPages > 1" class="pager">
      <button class="secondary" :disabled="entryPage <= 1 || detailLoading" @click="selectReport(selected, entryPage - 1)">上一页</button>
      <span>{{ entryPage }} / {{ entryPages }}</span>
      <button class="secondary" :disabled="entryPage >= entryPages || detailLoading" @click="selectReport(selected, entryPage + 1)">下一页</button>
    </div>
  </section>

  <div v-if="item" class="overlay" @click.self="item = undefined" @keydown.esc="item = undefined">
    <section class="dialog panel" role="dialog" aria-modal="true" aria-labelledby="monthly-item-title">
      <div class="panel-heading"><h2 id="monthly-item-title">月报明细</h2><button class="quiet" @click="item = undefined">关闭</button></div>
      <div class="dialog-body">
        <dl class="facts">
          <div><dt>学生</dt><dd>{{ item.studentName }} · {{ item.studentNo }}</dd></div>
          <div><dt>发生日期</dt><dd>{{ item.occurredOn }}</dd></div>
          <div><dt>班级</dt><dd>{{ item.className }}</dd></div>
          <div><dt>辅导员</dt><dd>{{ item.counselor }}</dd></div>
          <div><dt>违纪类型</dt><dd>{{ item.typeName }}</dd></div>
          <div><dt>检查项目</dt><dd>{{ item.inspectionLabel }}</dd></div>
          <div class="span-all"><dt>违纪原因</dt><dd>{{ item.reason }}</dd></div>
          <div class="span-all"><dt>学院处理意见</dt><dd>{{ item.disposition }}</dd></div>
          <div v-if="item.remarks" class="span-all"><dt>备注</dt><dd>{{ item.remarks }}</dd></div>
        </dl>
      </div>
    </section>
  </div>
</template>
