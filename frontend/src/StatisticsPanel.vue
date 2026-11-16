<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, errorText } from './api'
import type { StatisticsPeriod, ViolationStatistics } from './types'

const props = defineProps<{ query: string; revision: number; pendingOnly: boolean }>()
const period = ref<StatisticsPeriod>('WEEK')
const dimensionLabels = { byType: '违纪类型', byStatus: '状态', byDepartment: '部门', byClass: '班级' } as const
type Dimension = keyof typeof dimensionLabels
const dimension = ref<Dimension>('byType')
const stats = ref<ViolationStatistics>()
const loading = ref(false)
const problem = ref('')
let requestId = 0
const distribution = computed(() => stats.value?.[dimension.value] ?? [])
const trendMaximum = computed(() => Math.max(1, ...(stats.value?.trend.map(point => point.count) ?? [])))
const periodLabels = { DAY: '自然日', WEEK: '自然周', MONTH: '自然月' } as const
const trendLabel = (value: string) => period.value === 'MONTH' ? value.slice(0, 7) : value
const formatCount = new Intl.NumberFormat('zh-CN')
const share = (count: number) => stats.value?.total ? count * 100 / stats.value.total : 0

async function load() {
  const request = ++requestId
  problem.value = ''
  stats.value = undefined
  if (props.pendingOnly) { loading.value = false; return }
  loading.value = true
  const params = new URLSearchParams(props.query)
  params.set('period', period.value)
  try {
    const data = await api<ViolationStatistics>('/api/violations/statistics?' + params)
    if (requestId === request) stats.value = data
  } catch (e) {
    if (requestId === request) problem.value = errorText(e)
  } finally {
    if (requestId === request) loading.value = false
  }
}

watch(() => [props.query, props.revision, props.pendingOnly, period.value], load, { immediate: true })
</script>

<template>
  <section class="panel statistics-panel" aria-labelledby="statistics-heading">
    <div class="panel-heading">
      <div><h2 id="statistics-heading">正式记录统计</h2><small class="muted">按已应用的筛选条件统计全部结果，待审记录不计入。</small></div>
      <select v-if="!pendingOnly" v-model="period" class="stat-period" aria-label="时间统计粒度">
        <option v-for="(label, key) in periodLabels" :key="key" :value="key">{{ label }}</option>
      </select>
    </div>
    <p v-if="problem" class="error panel-message" role="alert">{{ problem }} <button class="text-button" @click="load">重试</button></p>
    <div v-else-if="loading" class="empty">正在统计…</div>
    <div v-else-if="pendingOnly" class="empty"><strong>待审核记录不计入正式统计</strong><span>切换到正式状态或清除状态筛选即可查看。</span></div>
    <div v-else-if="!stats?.total" class="empty"><strong>当前筛选下没有正式记录</strong></div>
    <div v-else class="statistics-body">
      <div class="stat-summary">
        <div><strong>{{ formatCount.format(stats.total) }}</strong><small>正式记录</small></div>
        <div><strong>{{ stats.byType.length }}</strong><small>涉及类型</small></div>
        <div><strong>{{ stats.byClass.length }}</strong><small>涉及班级</small></div>
      </div>
      <div class="stat-grid">
        <section class="stat-chart" aria-labelledby="trend-heading">
          <div class="stat-controls"><h3 id="trend-heading">时间分布</h3></div>
          <p v-if="period === 'WEEK' || stats.trendLimited" class="stat-note muted">
            <span v-if="period === 'WEEK'">以每周一为起始日。</span><span v-if="stats.trendLimited">趋势显示最近 {{ stats.trend.length }} 段。</span>
          </p>
          <ol class="stat-bars">
            <li v-for="point in stats.trend" :key="point.start">
              <span>{{ trendLabel(point.start) }}</span>
              <div class="stat-track" aria-hidden="true"><i :style="{ width: point.count * 100 / trendMaximum + '%' }"></i></div>
              <strong>{{ formatCount.format(point.count) }}</strong>
            </li>
          </ol>
        </section>
        <section class="stat-chart" aria-labelledby="group-heading">
          <div class="stat-controls"><h3 id="group-heading">按类别统计</h3>
            <select v-model="dimension" aria-label="类别统计维度"><option v-for="(label, key) in dimensionLabels" :key="key" :value="key">{{ label }}</option></select>
          </div>
          <ol class="stat-bars stat-groups">
            <li v-for="group in distribution" :key="group.key">
              <span>{{ group.label }}</span>
              <div class="stat-track" aria-hidden="true"><i :style="{ width: share(group.count) + '%' }"></i></div>
              <strong>{{ formatCount.format(group.count) }}</strong>
            </li>
          </ol>
        </section>
      </div>
    </div>
  </section>
</template>
