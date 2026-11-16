<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { api, downloadWorkbook, errorText } from './api'
import StatisticsPanel from './StatisticsPanel.vue'
import ViolationCorrectionDialog from './ViolationCorrectionDialog.vue'
import { stateNames } from './types'
import type { Entry, Group, Metadata, Page, Resolution, State, Student, Viewer } from './types'

const props = defineProps<{ viewer: Viewer }>()
const route = useRoute()
const meta = ref<Metadata>()
const classes = ref<Group[]>([])
const rows = ref<Entry[]>([])
const loading = ref(false)
const problem = ref('')
const linkedProblem = ref('')
const exporting = ref(false)
const showStatistics = ref(false)
const statisticsRevision = ref(0)
const page = ref(1)
const total = ref(0)
const size = 20
const selected = ref<number[]>([])
type Section = 'all' | 'pending' | 'formal'
const sections: { key: Section; label: string }[] = [
  { key: 'all', label: '全部记录' }, { key: 'pending', label: '待审核区' }, { key: 'formal', label: '正式记录' },
]
const prefersApproved = props.viewer.grants.some(g => g.roleCode === 'TEACHER' || g.roleCode === 'DISCIPLINE_OFFICE')
const section = ref<Section>(prefersApproved ? 'formal' : 'all')
const filter = reactive({ from: '', to: '', classId: '', department: '', type: '',
  status: prefersApproved ? 'APPROVED' : '', formalOnly: prefersApproved ? 'true' : '' })
const appliedFilter = ref({ ...filter })
const editing = ref<Entry>()
const detail = ref<Entry>()
const appealCode = ref('')
const correcting = ref<Entry>()
const open = ref(false)
const submitting = ref(false)
const candidates = ref<Student[]>([])
const candidate = ref<Student>()
const search = ref('')
const searched = ref(false)
const searching = ref(false)
const formProblem = ref('')
const form = reactive({ occurredOn: '', typeCode: '', reason: '', inspectionScope: '',
  disposition: '', remarks: '', submissionId: '' })
const allowedTypes = computed(() => meta.value?.types.filter(t => props.viewer.coreAdmin
  || props.viewer.officerDepartments.includes(t.departmentCode)) || [])
const statusChoices = computed(() => Object.entries(stateNames).filter(([code]) => section.value !== 'formal' || code !== 'PENDING'))
const selectedAppeal = computed(() => meta.value?.appealOptions.find(option => option.code === appealCode.value))
const requiresAppeal = computed(() => meta.value?.appealOptions.some(option => option.resolution === 'APPEALED') ?? false)
const historicalInspection = computed(() => editing.value && !meta.value?.inspectionOptions
  .some(option => option.code === editing.value?.inspectionScope))
const inspectionGroups = computed(() => {
  const options = meta.value?.inspectionOptions ?? []
  const categories = new Set(options.map(option => option.category || '未分类'))
  return [...categories].map(category => ({ category,
    options: options.filter(option => (option.category || '未分类') === category) }))
})
watch(() => detail.value?.id, () => { appealCode.value = '' })
const canCreate = computed(() => props.viewer.coreAdmin || props.viewer.officerDepartments.length > 0)
const last = computed(() => Math.max(1, Math.ceil(total.value / size)))
const reviewable = computed(() => rows.value.filter(r => r.reviewable))
const deptName = (code: string) => meta.value?.departments.find(d => d.code === code)?.name || code
const typeName = (code: string) => meta.value?.types.find(t => t.code === code)?.name || code

function filterParams(criteria = appliedFilter.value) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(criteria)) if (value) params.set(key, value)
  return params
}

async function load(target = 1, criteria = appliedFilter.value): Promise<boolean> {
  const params = filterParams(criteria)
  params.set('page', String(target))
  params.set('size', String(size))
  loading.value = true
  problem.value = ''
  selected.value = []
  try {
    const data = await api<Page>('/api/violations?' + params)
    rows.value = data.items
    total.value = data.total
    page.value = target
    statisticsRevision.value++
    if (target > last.value && !data.items.length) return await load(last.value, criteria)
    return true
  } catch (e) { problem.value = errorText(e); return false }
  finally { loading.value = false }
}

async function applyFilters() {
  const next = { ...filter }
  if (await load(1, next)) appliedFilter.value = next
}

async function selectSection(next: Section) {
  if (loading.value || section.value === next) return
  const criteria = { ...filter, status: next === 'pending' ? 'PENDING' : '',
    formalOnly: next === 'formal' ? 'true' : '' }
  if (await load(1, criteria)) {
    section.value = next
    Object.assign(filter, criteria)
    appliedFilter.value = criteria
  }
}

async function exportRecords(bulletin = false) {
  exporting.value = true
  try {
    const endpoint = bulletin ? 'bulletin' : 'export'
    await downloadWorkbook('/api/violations/' + endpoint + '?' + filterParams(), bulletin ? '违纪通报表.xlsx' : '正式违纪记录.xlsx')
  } catch (e) { ElMessage.error(errorText(e)) }
  finally { exporting.value = false }
}

async function openLinkedRecord() {
  linkedProblem.value = ''
  const id = route.query.record
  if (typeof id !== 'string' || !/^[1-9]\d*$/.test(id) || !props.viewer.canViewRecords) return
  try {
    const row = await api<Entry>('/api/violations/' + id)
    if (route.query.record === id) detail.value = row
  } catch (e) {
    if (route.query.record === id) linkedProblem.value = errorText(e)
  }
}

onMounted(async () => {
  if (!props.viewer.canViewRecords) return
  try {
    const [metadata, groups] = await Promise.all([api<Metadata>('/api/metadata'), api<Group[]>('/api/binding-classes')])
    meta.value = metadata
    classes.value = groups
    await load()
    await openLinkedRecord()
  } catch (e) { problem.value = errorText(e) }
})
watch(() => route.query.record, () => { if (meta.value) void openLinkedRecord() })

function showForm(row?: Entry) {
  detail.value = undefined
  editing.value = row
  formProblem.value = ''
  search.value = ''
  candidates.value = []
  candidate.value = undefined
  searched.value = false
  Object.assign(form, {
    occurredOn: row?.occurredOn || meta.value?.today || '', typeCode: row?.typeCode || '',
    reason: row?.reason || '', disposition: row?.disposition ?? meta.value?.defaultDisposition ?? '',
    inspectionScope: row?.inspectionScope || meta.value?.inspectionOptions[0]?.code || '', remarks: row?.remarks || '',
    submissionId: row ? '' : crypto.randomUUID(),
  })
  open.value = true
}
async function lookup() {
  if (!search.value.trim() || searching.value) return
  searching.value = true
  searched.value = false
  formProblem.value = ''
  candidate.value = undefined
  try {
    candidates.value = await api<Student[]>('/api/students/lookup?' + new URLSearchParams({ q: search.value.trim() }))
    if (candidates.value.length === 1) candidate.value = candidates.value[0]
    searched.value = true
  } catch (e) { formProblem.value = errorText(e) }
  finally { searching.value = false }
}
async function save() {
  if (!editing.value && !candidate.value) { formProblem.value = '请先查找并选择学生'; return }
  submitting.value = true
  formProblem.value = ''
  const fields = { reason: form.reason, disposition: form.disposition, inspectionScope: form.inspectionScope, remarks: form.remarks || null }
  try {
    if (editing.value) await api('/api/violations/' + editing.value.id, 'PUT', { ...fields, version: editing.value.version })
    else await api('/api/violations', 'POST', { ...fields, studentNo: candidate.value?.studentNo,
      occurredOn: form.occurredOn, typeCode: form.typeCode, submissionId: form.submissionId })
    open.value = false
    ElMessage.success(editing.value ? '修改已保存' : '已提交审核')
    await load(page.value)
  } catch (e) { formProblem.value = errorText(e) }
  finally { submitting.value = false }
}
async function review(action: 'APPROVE' | 'REJECT') {
  if (!selected.value.length) return
  let reason: string | undefined
  try {
    if (action === 'REJECT') {
      const result = await ElMessageBox.prompt('记录会保留在待审核区，请填写退回给录入人的原因。', '批量驳回',
        { confirmButtonText: '驳回', cancelButtonText: '取消', inputValidator: v => !!v?.trim() && v.length <= 250 || '请输入 1–250 个字符' })
      reason = (result as unknown as { value: string }).value
    } else await ElMessageBox.confirm('将审核通过所选的 ' + selected.value.length + ' 条记录。', '批量审核',
      { confirmButtonText: '确认通过', cancelButtonText: '取消' })
  } catch { return }
  submitting.value = true
  try {
    const result = await api<{ changed: number[]; skipped: number[] }>('/api/violations/review', 'POST',
      { action, reason, items: reviewable.value.filter(r => selected.value.includes(r.id)).map(r => ({ id: r.id, version: r.version })) })
    ElMessage({ type: result.skipped.length ? 'warning' : 'success',
      message: '已处理 ' + result.changed.length + ' 条，跳过 ' + result.skipped.length + ' 条' })
    await load(page.value)
  } catch (e) { ElMessage.error(errorText(e)) }
  finally { submitting.value = false }
}
async function change(row: Entry, action: 'DELETE' | Resolution) {
  const label = action === 'DELETE' ? '删除' : '标记为' + stateNames[action]
  const outcome = selectedAppeal.value
  try {
    await ElMessageBox.confirm('确认' + label + '这条记录？', label, { confirmButtonText: '确认', cancelButtonText: '取消' })
  } catch { return }
  submitting.value = true
  try {
    if (action === 'DELETE') await api('/api/violations/' + row.id + '?version=' + row.version, 'DELETE')
    else await api('/api/violations/' + row.id + '/resolve', 'POST', {
      version: row.version, resolution: action, appealCode: outcome?.code,
    })
    detail.value = undefined
    ElMessage.success('操作成功')
    await load(page.value)
  } catch (e) { ElMessage.error(errorText(e)) }
  finally { submitting.value = false }
}
function reset() {
  Object.assign(filter, { from: '', to: '', classId: '', department: '', type: '',
    status: section.value === 'pending' ? 'PENDING' : '' })
  void applyFilters()
}

function startCorrection(record: Entry) {
  detail.value = undefined
  correcting.value = record
}

function cancelCorrection() {
  detail.value = correcting.value
  correcting.value = undefined
}

async function correctionSaved(record: Entry) {
  correcting.value = undefined
  detail.value = record
  ElMessage.success('纠错已保存')
  await load(page.value)
}
</script>

<template>
  <div class="page-header">
    <div><p class="eyebrow">日常管理</p><h1>违纪记录</h1><p class="muted">{{ viewer.allRecords ? '筛选学院记录，跟进审核和学生处理情况。' : '查看当前身份权限范围内的违纪记录。' }}</p></div>
    <div class="buttons">
      <button v-if="viewer.canViewStatistics" class="secondary" :aria-expanded="showStatistics"
        @click="showStatistics = !showStatistics">{{ showStatistics ? '收起统计' : '查看统计' }}</button>
      <button v-if="canCreate && meta" class="primary" @click="showForm()">＋ 登记违纪</button>
    </div>
  </div>
  <div v-if="!viewer.canViewRecords" class="panel empty"><strong>当前身份无原始明细访问权限</strong></div>
  <template v-else>
    <p v-if="linkedProblem" class="error linked-notice-error" role="alert">通知关联的记录暂不可查看：{{ linkedProblem }}</p>
    <nav class="tabs" aria-label="记录工作区">
      <button v-for="item in sections" :key="item.key" :class="{ current: section === item.key }"
        :aria-pressed="section === item.key" :disabled="loading || !meta" @click="selectSection(item.key)">{{ item.label }}</button>
    </nav>
    <section class="panel records-filter-panel" aria-labelledby="records-filter-title">
      <div class="panel-heading"><h2 id="records-filter-title">筛选记录</h2></div>
      <form class="filters" @submit.prevent="applyFilters">
        <label>开始日期<input v-model="filter.from" type="date"></label>
        <label>结束日期<input v-model="filter.to" type="date"></label>
        <label>班级<select v-model="filter.classId"><option value="">全部班级</option><option v-for="c in classes" :key="c.id" :value="String(c.id)">{{ c.name }}</option></select></label>
        <label>部门<select v-model="filter.department"><option value="">全部部门</option><option v-for="d in meta?.departments" :key="d.code" :value="d.code">{{ d.name }}</option></select></label>
        <label>类型<select v-model="filter.type"><option value="">全部类型</option><option v-for="t in meta?.types" :key="t.code" :value="t.code">{{ t.name }}</option></select></label>
        <label v-if="section !== 'pending'">状态<select v-model="filter.status"><option value="">全部状态</option><option v-for="[code, name] in statusChoices" :key="code" :value="code">{{ name }}</option></select></label>
        <div class="buttons"><button class="primary" :disabled="loading || !meta">查询</button><button type="button" class="secondary" :disabled="loading || !meta" @click="reset">重置</button></div>
      </form>
    </section>
    <StatisticsPanel v-if="viewer.canViewStatistics && showStatistics && meta"
      :query="filterParams().toString()" :revision="statisticsRevision" :pending-only="appliedFilter.status === 'PENDING'" />
    <section class="panel table-panel" aria-label="违纪记录列表">
      <div class="panel-heading">
        <div><h2>记录列表</h2><small class="muted">共 {{ total }} 条记录</small></div>
        <div class="buttons">
          <button v-if="viewer.allRecords" class="secondary"
            :disabled="!meta || loading || exporting || !!problem || appliedFilter.status === 'PENDING'"
            title="导出已应用筛选条件下的正式记录，待审不导出"
            @click="exportRecords()">{{ exporting ? '正在导出…' : '导出正式记录' }}</button>
          <button v-if="viewer.allRecords" class="secondary"
            :disabled="exporting || loading || appliedFilter.status === 'PENDING' || appliedFilter.status === 'APPEALED'"
            title="按当前筛选导出通报表，排除待审核和已申诉记录"
            @click="exportRecords(true)">导出通报表</button>
          <template v-if="selected.length">
            <button class="secondary" :disabled="submitting" @click="review('REJECT')">批量驳回</button>
            <button class="primary" :disabled="submitting" @click="review('APPROVE')">通过（{{ selected.length }}）</button>
          </template>
        </div>
      </div>
      <p v-if="problem" class="error panel-message" role="alert">{{ problem }}</p>
      <div v-else-if="loading || !meta" class="empty">正在加载记录…</div>
      <div v-else-if="!rows.length" class="empty"><strong>没有符合条件的记录</strong><span>可以调整上方筛选条件后再查询。</span></div>
      <table v-else class="record-table">
        <thead><tr>
          <th v-if="reviewable.length" class="check-cell"><input type="checkbox" aria-label="选择本页可审核记录" :checked="selected.length === reviewable.length"
            @change="selected = ($event.target as HTMLInputElement).checked ? reviewable.map(r => r.id) : []"></th>
          <th>发生日期</th><th>学生</th><th>班级</th><th>违纪类型</th><th>所属部门</th><th>状态</th><th>操作</th>
        </tr></thead>
        <tbody><tr v-for="r in rows" :key="r.id">
          <td v-if="reviewable.length" class="check-cell"><input v-if="r.reviewable" v-model="selected" type="checkbox" :value="r.id" :aria-label="'选择 ' + r.studentName + ' 的记录'"></td>
          <td data-label="发生日期">{{ r.occurredOn }}</td>
          <td data-label="学生"><strong>{{ r.studentName }}</strong><small class="cell-sub">{{ r.studentNo }}</small></td>
          <td data-label="班级">{{ r.className }}</td>
          <td data-label="违纪类型">{{ typeName(r.typeCode) }}</td>
          <td data-label="所属部门">{{ deptName(r.departmentCode) }}</td>
          <td data-label="状态"><span class="state" :class="r.status.toLowerCase()">{{ r.rejectionReason ? '待审核 · 已驳回' : stateNames[r.status] }}</span></td>
          <td class="row-actions"><button class="text-button" @click="detail = r">详情</button><button v-if="r.editable" class="text-button" @click="showForm(r)">编辑</button></td>
        </tr></tbody>
      </table>
      <div v-if="total > size" class="pager"><button class="secondary" :disabled="page <= 1 || loading" @click="load(page - 1)">上一页</button><span>{{ page }} / {{ last }}</span><button class="secondary" :disabled="page >= last || loading" @click="load(page + 1)">下一页</button></div>
    </section>
  </template>

  <div v-if="detail" class="overlay" @click.self="detail = undefined">
    <section class="dialog panel" role="dialog" aria-modal="true" aria-labelledby="detail-title">
      <div class="panel-heading"><h2 id="detail-title">违纪详情</h2><button class="quiet" @click="detail = undefined">关闭</button></div>
      <div class="dialog-body">
        <dl class="facts">
          <div><dt>学生</dt><dd>{{ detail.studentName }} · {{ detail.studentNo }}</dd></div>
          <div><dt>班级</dt><dd>{{ detail.className }}</dd></div>
          <div><dt>辅导员</dt><dd>{{ detail.counselor }}</dd></div>
          <div><dt>日期</dt><dd>{{ detail.occurredOn }}</dd></div>
          <div><dt>类型</dt><dd>{{ typeName(detail.typeCode) }}</dd></div>
          <div><dt>状态</dt><dd>{{ stateNames[detail.status] }}</dd></div>
          <div><dt>检查项目</dt><dd>{{ detail.inspectionLabel }}</dd></div>
          <div v-if="detail.appealLabel"><dt>申诉情形</dt><dd>{{ detail.appealLabel }}</dd></div>
          <div class="span-all"><dt>违纪原因</dt><dd>{{ detail.reason }}</dd></div>
          <div class="span-all"><dt>处理意见</dt><dd>{{ detail.disposition }}</dd></div>
          <div v-if="detail.rejectionReason" class="span-all"><dt>驳回原因</dt><dd>{{ detail.rejectionReason }}</dd></div>
          <div v-if="detail.remarks" class="span-all"><dt>备注</dt><dd>{{ detail.remarks }}</dd></div>
        </dl>
        <label v-if="detail.resolvable && meta?.appealOptions.length" class="resolution-choice">申诉情形
          <select v-model="appealCode" :disabled="submitting">
            <option value="">未选择申诉情形</option>
            <option v-for="option in meta.appealOptions" :key="option.code" :value="option.code">
              {{ option.label }} · {{ option.resolution === 'APPEALED' ? '已申诉' : '已交回' }}
            </option>
          </select>
        </label>
        <div class="buttons">
          <template v-if="detail.resolvable">
            <button class="primary" :disabled="submitting || selectedAppeal?.resolution === 'APPEALED'"
              @click="change(detail, 'HANDED_BACK')">标记已交回</button>
            <button class="secondary" :disabled="submitting || selectedAppeal?.resolution === 'HANDED_BACK' || (requiresAppeal && !selectedAppeal)"
              @click="change(detail, 'APPEALED')">标记已申诉</button>
          </template>
          <button v-if="detail.editable" class="danger" :disabled="submitting" @click="change(detail, 'DELETE')">删除</button>
          <button v-if="detail.correctable && meta" class="secondary" :disabled="submitting"
            @click="startCorrection(detail)">纠错</button>
        </div>
      </div>
    </section>
  </div>

  <div v-if="open" class="overlay" @click.self="!submitting && (open = false)">
    <section class="dialog panel" role="dialog" aria-modal="true" aria-labelledby="form-title">
      <div class="panel-heading"><h2 id="form-title">{{ editing ? '编辑记录' : '登记违纪' }}</h2><button class="quiet" :disabled="submitting" @click="open = false">关闭</button></div>
      <form class="fields dialog-body" @submit.prevent="save">
        <template v-if="!editing">
          <label>查找学生<div class="input-group"><input v-model.trim="search" placeholder="完整学号或姓名" maxlength="80" @input="candidate = undefined; searched = false" @keydown.enter.prevent="lookup"><button type="button" class="secondary" :disabled="searching || !search" @click="lookup">查找</button></div></label>
          <p v-if="searched && !candidates.length" class="muted">未找到匹配的有效学籍。</p>
          <label v-else-if="searched && candidates.length > 1">选择匹配学生<select v-model="candidate" required><option :value="undefined">请选择</option><option v-for="c in candidates" :key="c.id" :value="c">{{ c.name }} · {{ c.className }} · {{ c.studentNo }}</option></select></label>
          <div v-if="candidate" class="candidate"><strong>{{ candidate.name }} · {{ candidate.studentNo }}</strong><span>{{ candidate.className }} / 辅导员 {{ candidate.counselor }}</span></div>
          <div class="two-col">
            <label>发生日期<input v-model="form.occurredOn" type="date" :max="meta?.today" required></label>
            <label>违纪类型<select v-model="form.typeCode" required><option value="">请选择</option><option v-for="t in allowedTypes" :key="t.code" :value="t.code">{{ t.name }}</option></select></label>
          </div>
        </template>
        <p v-else class="muted">{{ editing.studentName }} · {{ editing.studentNo }} / {{ editing.occurredOn }}</p>
        <label>违纪原因<textarea v-model.trim="form.reason" required maxlength="500" rows="3"></textarea></label>
        <label>学院处理意见<input v-model.trim="form.disposition" required maxlength="250"></label>
        <label>检查项目<select v-model="form.inspectionScope" required>
          <option v-if="historicalInspection && editing" :value="editing.inspectionScope">{{ editing.inspectionLabel }}（历史）</option>
          <optgroup v-for="group in inspectionGroups" :key="group.category" :label="group.category">
            <option v-for="option in group.options" :key="option.code" :value="option.code">
              {{ editing?.inspectionScope === option.code ? editing.inspectionLabel : option.label }}
            </option>
          </optgroup>
        </select></label>
        <label>备注（选填）<input v-model.trim="form.remarks" maxlength="250"></label>
        <p v-if="formProblem" class="error" role="alert">{{ formProblem }}</p>
        <div class="buttons"><button class="primary" :disabled="submitting">{{ submitting ? '保存中…' : editing ? '保存修改' : '提交审核' }}</button></div>
      </form>
    </section>
  </div>

  <ViolationCorrectionDialog v-if="correcting && meta" :record="correcting" :metadata="meta"
    @cancel="cancelCorrection" @saved="correctionSaved" />
</template>
