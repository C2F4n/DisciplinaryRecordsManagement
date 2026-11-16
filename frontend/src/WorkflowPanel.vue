<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorText } from './api'
import type { Dept, Resolution } from './types'

type Option = {
  id: number; kind: 'INSPECTION' | 'APPEAL'; code: string; label: string | null
  resolution: Resolution | null; enabled: boolean; version: number; category: string | null
}
type TypeAssignment = { code: string; name: string; departmentCode: string; version: number }
type Settings = { options: Option[]; types: TypeAssignment[]; departments: Dept[] }

defineProps<{ section: 'inspections' | 'appeals' | 'types' }>()
const path = '/api/admin/workflow'
const settings = ref<Settings>()
const inspections = computed(() => settings.value?.options.filter(option => option.kind === 'INSPECTION') || [])
const appealGroups: { resolution: Resolution; label: string }[] = [
  { resolution: 'HANDED_BACK', label: '申诉失败，标记为已交回' },
  { resolution: 'APPEALED', label: '申诉成功，标记为已申诉' },
]
const appeals = (resolution: Resolution) => settings.value?.options.filter(option => option.resolution === resolution) || []
const inspectionName = ref('')
const inspectionCategory = ref('')
const categoryDrafts = reactive<Record<string, string>>({})
const categoryFilter = ref('')
const page = ref(1)
const pageSize = 8
const categories = computed(() => [...new Set(inspections.value.map(option => option.category).filter(Boolean))])
const filtered = computed(() => inspections.value.filter(option =>
  !categoryFilter.value || (option.category || '未分类') === categoryFilter.value))
const last = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const pageItems = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(categoryFilter, () => { page.value = 1 })
watch(categories, values => {
  if (categoryFilter.value && categoryFilter.value !== '未分类' && !values.includes(categoryFilter.value)) {
    categoryFilter.value = ''
  }
})
watch(last, value => { if (page.value > value) page.value = value })
const busy = ref(false)
const problem = ref('')

async function run(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  problem.value = ''
  try { await task() }
  catch (e) { problem.value = errorText(e) }
  finally { busy.value = false }
}

const load = () => run(async () => {
  settings.value = await api<Settings>(path)
  for (const option of settings.value.options) categoryDrafts[option.code] = option.category ?? ''
})
onMounted(load)

const addInspection = () => run(async () => {
  const created = await api<Option>(path + '/inspections', 'POST', { label: inspectionName.value, category: inspectionCategory.value })
  settings.value?.options.push(created)
  categoryDrafts[created.code] = created.category ?? ''
  inspectionName.value = ''
  inspectionCategory.value = ''
  ElMessage.success('检查项目已添加')
})

const saveOption = (option: Option) => run(async () => {
  const { label, enabled, version } = option
  const category = option.kind === 'INSPECTION' ? categoryDrafts[option.code] ?? '' : undefined
  const updated = await api<Option>(path + '/options/' + option.code, 'PUT', { label, enabled, version, category })
  Object.assign(option, updated)
  categoryDrafts[option.code] = updated.category ?? ''
  ElMessage.success('选项已保存')
})

async function deleteInspection(option: Option) {
  if (busy.value) return
  try {
    await ElMessageBox.confirm('删除检查项目「' + option.label + '」？历史记录将保留原项目名称。', '删除检查项目',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  await run(async () => {
    await api(path + '/inspections/' + option.code + '?version=' + option.version, 'DELETE')
    if (settings.value) settings.value.options = settings.value.options.filter(current => current.code !== option.code)
    delete categoryDrafts[option.code]
    ElMessage.success('检查项目已删除')
  })
}

const saveType = (type: TypeAssignment) => run(async () => {
  const { departmentCode, version } = type
  const updated = await api<TypeAssignment>(path + '/types/' + type.code, 'PUT', { departmentCode, version })
  Object.assign(type, updated)
  ElMessage.success('部门归属已保存')
})
</script>

<template>
  <div class="template-toolbar">
    <button class="secondary" :disabled="busy" @click="load">刷新</button>
  </div>
  <p v-if="problem" class="error template-error" role="alert">{{ problem }}</p>
  <div v-if="settings" class="management-stack">
    <template v-if="section === 'inspections'">
      <section class="panel" aria-labelledby="inspection-create-title">
        <div class="panel-heading"><h2 id="inspection-create-title">新增检查项目</h2></div>
        <form class="management-form inspection-create" @submit.prevent="addInspection">
          <label>项目名称<input v-model.trim="inspectionName" maxlength="80" required :disabled="busy"></label>
          <label>分类<input v-model.trim="inspectionCategory" maxlength="80" list="inspection-categories"
            placeholder="选择或输入分类" :disabled="busy"></label>
          <button class="primary" :disabled="busy">添加项目</button>
        </form>
        <datalist id="inspection-categories">
          <option v-for="category in categories" :key="category ?? ''" :value="category ?? ''" />
        </datalist>
      </section>
      <section class="panel" aria-labelledby="inspection-list-title">
        <div class="panel-heading"><h2 id="inspection-list-title">项目列表与分类</h2></div>
        <div class="management-form">
          <div class="inspection-filter">
            <label>分类筛选<select v-model="categoryFilter">
              <option value="">全部分类</option>
              <option v-for="category in categories" :key="category ?? ''" :value="category">{{ category }}</option>
              <option v-if="!categories.includes('未分类')" value="未分类">未分类</option>
            </select></label>
          </div>
          <div class="workflow-list">
            <form v-for="option in pageItems" :key="option.code" class="inspection-row" @submit.prevent="saveOption(option)">
              <input v-model.trim="option.label" aria-label="检查项目名称" maxlength="80" required :disabled="busy">
              <input v-model.trim="categoryDrafts[option.code]" aria-label="检查项目分类" maxlength="80" list="inspection-categories"
                placeholder="未分类" :disabled="busy">
              <label class="workflow-enabled"><input v-model="option.enabled" type="checkbox" :disabled="busy">启用</label>
              <div class="buttons">
                <button class="secondary" :disabled="busy">保存</button>
                <button type="button" class="danger" :disabled="busy" @click="deleteInspection(option)">删除</button>
              </div>
            </form>
          </div>
          <p v-if="!pageItems.length" class="muted">该分类暂无检查项目。</p>
        </div>
        <div v-if="last > 1" class="pager">
          <button class="secondary" :disabled="page <= 1 || busy" @click="page--">上一页</button>
          <span>{{ page }} / {{ last }}</span>
          <button class="secondary" :disabled="page >= last || busy" @click="page++">下一页</button>
        </div>
      </section>
    </template>

    <section v-else-if="section === 'appeals'" class="panel admin-card">
      <h2>申诉情形</h2>
      <p class="muted">填写学院提供的名称后启用。学生和班委端显示处理状态。</p>
      <div v-for="group in appealGroups" :key="group.resolution" class="workflow-list">
        <h3>{{ group.label }}</h3>
        <form v-for="(option, index) in appeals(group.resolution)" :key="option.code" class="workflow-row" @submit.prevent="saveOption(option)">
          <input v-model.trim="option.label" :aria-label="group.label + '，情形 ' + (index + 1)"
            :placeholder="'情形 ' + (index + 1) + ' 名称'" maxlength="80" :required="option.enabled" :disabled="busy">
          <label class="workflow-enabled"><input v-model="option.enabled" type="checkbox" :disabled="busy">启用</label>
          <button class="secondary" :disabled="busy">保存</button>
        </form>
      </div>
    </section>

    <section v-else class="panel admin-card">
      <h2>违纪类型的部门归属</h2>
      <p class="muted">调整后的归属用于新登记。历史记录按当时的部门保留。</p>
      <div class="workflow-list">
        <form v-for="type in settings.types" :key="type.code" class="workflow-type-row" @submit.prevent="saveType(type)">
          <label :for="'department-' + type.code">{{ type.name }}</label>
          <select :id="'department-' + type.code" v-model="type.departmentCode" :disabled="busy">
            <option v-for="dept in settings.departments" :key="dept.code" :value="dept.code">{{ dept.name }}</option>
          </select>
          <button class="secondary" :disabled="busy">保存</button>
        </form>
      </div>
    </section>
  </div>
</template>
