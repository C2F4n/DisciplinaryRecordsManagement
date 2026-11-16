<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, downloadWorkbook, errorText } from './api'

const props = defineProps<{ studentNumberHint?: string; mode?: 'classes' }>()
const emit = defineEmits<{ rosterChanged: [] }>()
const path = computed(() => '/api/admin/roster' + (props.mode === 'classes' ? '/class-import' : ''))

type RosterCheck = { rows: number; ready: boolean; issues: { row: number; message: string }[] }
const input = ref<HTMLInputElement>()
const file = ref<File>()
const checked = ref<RosterCheck>()
const busy = ref<'template' | 'check' | 'import'>()
const problem = ref('')
const success = ref('')

function choose(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0]
  checked.value = undefined
  problem.value = ''
  success.value = ''
}

function upload(file: File) {
  const form = new FormData()
  form.append('file', file)
  return form
}

async function templateFile() {
  busy.value = 'template'
  problem.value = ''
  try { await downloadWorkbook(path.value + '/template', props.mode === 'classes' ? '班级与辅导员导入模板.xlsx' : '学生名册导入模板.xlsx') }
  catch (e) { problem.value = errorText(e) }
  finally { busy.value = undefined }
}

async function check() {
  if (!file.value) return
  busy.value = 'check'
  problem.value = ''
  success.value = ''
  checked.value = undefined
  try { checked.value = await api<RosterCheck>(path.value + '/check', 'POST', upload(file.value)) }
  catch (e) { problem.value = errorText(e) }
  finally { busy.value = undefined }
}

async function importRows() {
  if (!file.value || !checked.value?.ready) return
  busy.value = 'import'
  problem.value = ''
  try {
    if (props.mode === 'classes') {
      const result = await api<{ classCount: number; counselorCount: number }>(path.value + '/import', 'POST', upload(file.value))
      success.value = '已导入 ' + result.classCount + ' 个班级，新增 ' + result.counselorCount + ' 名辅导员。'
    } else {
      const result = await api<{ studentCount: number }>(path.value + '/import', 'POST', upload(file.value))
      success.value = '已导入 ' + result.studentCount + ' 名学生，学生可凭名册信息注册。'
    }
    checked.value = undefined
    file.value = undefined
    if (input.value) input.value.value = ''
    emit('rosterChanged')
  } catch (e) {
    checked.value = undefined
    problem.value = errorText(e)
  } finally { busy.value = undefined }
}
</script>

<template>
  <section class="panel" aria-labelledby="roster-import-title">
    <div class="panel-heading">
      <div><h2 id="roster-import-title">{{ mode === 'classes' ? '批量导入班级与辅导员' : '批量导入学生名册' }}</h2>
        <small v-if="mode !== 'classes'" class="muted">班级须已在系统中创建。</small>
      </div>
      <button class="secondary" :disabled="!!busy" @click="templateFile">下载 Excel 模板</button>
    </div>
    <div class="roster-import-body">
      <label><span>名册文件 <span class="required-mark" aria-hidden="true">*</span></span>
        <input ref="input" type="file" accept=".xlsx" required :disabled="!!busy" @change="choose">
      </label>
      <p v-if="mode === 'classes'" class="muted">填写班级名称及 1–3 名辅导员。班级留空时仅录入辅导员，名单已有的姓名会直接复用。</p>
      <p v-else class="muted">保留模板表头。学号按文本填写，手机号可留空，入学日期用 YYYY-MM-DD。<span v-if="studentNumberHint">学号须为{{ studentNumberHint }}。</span></p>
      <div class="buttons">
        <button class="secondary" :disabled="!file || !!busy" @click="check">{{ busy === 'check' ? '校验中…' : '校验文件' }}</button>
        <button class="primary" :disabled="!checked?.ready || !!busy" @click="importRows">{{ busy === 'import' ? '导入中…' : '确认导入' }}</button>
      </div>
      <p v-if="problem" class="error" role="alert">{{ problem }}</p>
      <p v-if="success" class="success" role="status">{{ success }}</p>
      <p v-if="checked?.ready" class="success" role="status">{{ checked.rows }} 行全部通过校验，可以确认导入。</p>
      <div v-else-if="checked" class="roster-issues" role="alert">
        <p><strong>{{ checked.issues.length }} 行需修改</strong>。本次校验没有写入名册。</p>
        <ul><li v-for="issue in checked.issues" :key="issue.row"><strong>第 {{ issue.row }} 行：</strong>{{ issue.message }}</li></ul>
      </div>
    </div>
  </section>
</template>
