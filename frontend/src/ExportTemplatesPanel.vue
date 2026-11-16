<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorText } from './api'

type Kind = 'MONTHLY_REPORT' | 'FILTERED_VIOLATIONS' | 'BULLETIN'
type Column = { code: string; label: string; required: boolean }
type Template = { kind: Kind; title: string; columns: string[]; availableColumns: Column[]; version: number }

const path = '/api/admin/export-templates'
const names: Record<Kind, string> = {
  MONTHLY_REPORT: '违纪统计月报', FILTERED_VIOLATIONS: '正式违纪记录', BULLETIN: '违纪通报表',
}
const templates = ref<Template[]>([])
const activeKind = ref<Kind>('MONTHLY_REPORT')
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

const refresh = () => run(async () => { templates.value = await api<Template[]>(path) })
onMounted(refresh)

function columns(template: Template) {
  const options = new Map(template.availableColumns.map(option => [option.code, option]))
  return template.columns.flatMap(code => options.get(code) ?? [])
}

function move(template: Template, index: number, direction: -1 | 1) {
  const next = index + direction
  const current = template.columns[index]
  const other = template.columns[next]
  if (!current || !other) return
  template.columns.splice(Math.min(index, next), 2, ...(direction < 0 ? [current, other] : [other, current]))
}

const save = (template: Template) => run(async () => {
  const { kind, title, columns, version } = template
  const saved = await api<Template>(path + '/' + kind, 'PUT', { title, columns, version })
  templates.value = templates.value.map(current => current.kind === saved.kind ? saved : current)
  ElMessage.success('模板已保存')
})
</script>

<template>
  <section :aria-busy="busy">
    <div class="template-toolbar">
      <p class="muted">模板用于之后的导出和月报生成。已归档月报保留生成时的文件。</p>
      <button class="secondary" :disabled="busy" @click="refresh">刷新模板</button>
    </div>
    <p v-if="problem" class="error template-error" role="alert">{{ problem }}</p>
    <nav class="tabs admin-tabs" aria-label="选择导出模板">
      <button v-for="template in templates" :key="template.kind" :class="{ current: activeKind === template.kind }"
        :aria-pressed="activeKind === template.kind" :disabled="busy" @click="activeKind = template.kind">
        {{ names[template.kind] }}
      </button>
    </nav>
    <div class="template-editor">
      <section v-for="template in templates.filter(item => item.kind === activeKind)" :key="template.kind" class="panel admin-card">
        <h2>{{ names[template.kind] }}设置</h2>
        <form class="fields maintenance-form" @submit.prevent="save(template)">
          <label>工作表内标题<input v-model.trim="template.title" maxlength="80" required :disabled="busy"></label>
          <div>
            <h3>导出列顺序</h3>
            <ol class="template-columns">
              <li v-for="(column, index) in columns(template)" :key="column.code">
                <span>{{ column.label }}</span>
                <div class="buttons">
                  <button type="button" class="text-button" :aria-label="'上移' + column.label"
                    :disabled="busy || index === 0" @click="move(template, index, -1)">上移</button>
                  <button type="button" class="text-button" :aria-label="'下移' + column.label"
                    :disabled="busy || index === template.columns.length - 1" @click="move(template, index, 1)">下移</button>
                  <button v-if="!column.required" type="button" class="text-button" :aria-label="'隐藏' + column.label"
                    :disabled="busy" @click="template.columns.splice(index, 1)">隐藏</button>
                </div>
              </li>
            </ol>
          </div>
          <div class="buttons">
            <template v-for="column in template.availableColumns" :key="column.code">
              <button v-if="!template.columns.includes(column.code)" type="button" class="secondary"
                :disabled="busy" @click="template.columns.push(column.code)">添加{{ column.label }}</button>
            </template>
          </div>
          <button class="primary" :disabled="busy">保存模板</button>
        </form>
      </section>
    </div>
  </section>
</template>
