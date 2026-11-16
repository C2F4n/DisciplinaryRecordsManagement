<script setup lang="ts">
import { computed, nextTick, onActivated, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorText } from './api'
import CounselorSelect from './CounselorSelect.vue'
import { maxClassCounselors } from './types'
import type { Counselor, ManagedClass, Metadata, RosterStudent, StudentNumberRule } from './types'

type StudentPage = { items: RosterStudent[]; total: number }

const path = '/api/admin/roster'
const pageSize = 20
const classes = ref<ManagedClass[]>([])
const counselors = ref<Counselor[]>([])
const classId = ref(0)
const classEdit = ref<ManagedClass>()
const rule = ref<StudentNumberRule>()
const today = ref('')
const filter = ref({ classId: 0, query: '' })
let appliedFilter = { classId: 0, query: '' }
const students = ref<StudentPage>({ items: [], total: 0 })
const page = ref(1)
const pageCount = computed(() => Math.max(1, Math.ceil(students.value.total / pageSize)))
const ready = ref(false)
const busy = ref(false)
const problem = ref('')
const editor = ref<HTMLDialogElement>()
const studentEdit = ref<RosterStudent>()
const originalClass = ref(0)
const studentError = ref('')

async function run(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  problem.value = ''
  try { await task() }
  catch (e) { problem.value = errorText(e) }
  finally { busy.value = false }
}

function selectClass() {
  const group = classes.value.find(group => group.id === classId.value)
  classEdit.value = group ? { ...group, counselors: [...group.counselors] } : undefined
}

async function loadStudents(index: number, criteria = appliedFilter) {
  const params = new URLSearchParams({ page: String(index), size: String(pageSize), query: criteria.query })
  if (criteria.classId) params.set('classId', String(criteria.classId))
  students.value = await api<StudentPage>(path + '/students?' + params)
  appliedFilter = criteria
  page.value = index
}

const refresh = () => run(async () => {
  const [groups, currentRule, metadata, counselorList] = await Promise.all([
    api<ManagedClass[]>(path + '/classes'),
    api<StudentNumberRule>(path + '/student-number-rule'),
    api<Pick<Metadata, 'today'>>('/api/metadata'),
    api<Counselor[]>('/api/admin/counselors'),
    loadStudents(1, { ...filter.value }),
  ])
  classes.value = groups
  selectClass()
  rule.value = currentRule
  today.value = metadata.today
  counselors.value = counselorList
  ready.value = true
})

onActivated(refresh)

const saveClass = () => run(async () => {
  const draft = classEdit.value
  if (!draft) return
  const { id, name, counselors, version } = draft
  const saved = await api<ManagedClass>(path + '/classes/' + id, 'PUT', { name, counselors, version })
  classes.value = classes.value.map(group => group.id === id ? saved : group)
  selectClass()
  ElMessage.success('班级已保存')
  await loadStudents(page.value)
})

const saveRule = () => run(async () => {
  const saved = await api<StudentNumberRule>(path + '/student-number-rule', 'PUT', rule.value)
  rule.value = saved
  ElMessage.success('学号规则已保存')
})

async function editStudent(student: RosterStudent) {
  studentEdit.value = { ...student }
  originalClass.value = student.classId
  studentError.value = ''
  await nextTick()
  editor.value?.showModal()
}

async function saveStudent() {
  const draft = studentEdit.value
  if (busy.value || !draft) return
  busy.value = true
  studentError.value = ''
  const { id, name, classId, phone, validUntil, version } = draft
  try {
    await api<RosterStudent>(path + '/students/' + id, 'PUT', { name, classId, phone, validUntil, version })
    editor.value?.close()
    ElMessage.success('学籍信息已保存')
  } catch (e) {
    studentError.value = errorText(e)
    return
  } finally {
    busy.value = false
  }
  await run(() => loadStudents(1))
}
</script>

<template>
  <p v-if="problem" class="error" role="alert">{{ problem }}</p>
  <section class="panel" aria-labelledby="edit-class-title">
    <div class="panel-heading"><h2 id="edit-class-title">修改班级和辅导员</h2></div>
    <form class="fields management-form" @submit.prevent="saveClass">
      <label>选择班级<select v-model="classId" :disabled="busy" @change="selectClass">
        <option :value="0">请选择</option>
        <option v-for="group in classes" :key="group.id" :value="group.id">{{ group.name }}</option>
      </select></label>
      <div v-if="classEdit" class="two-col">
        <label>班级名称<input v-model.trim="classEdit.name" required maxlength="100" :disabled="busy"></label>
        <label for="edit-class-counselor"><span>辅导员 <span class="required-mark" aria-hidden="true">*</span></span>
          <CounselorSelect id="edit-class-counselor" v-model="classEdit.counselors" :options="counselors" :disabled="busy" />
          <small class="muted">最多选择 {{ maxClassCounselors }} 人</small>
        </label>
      </div>
      <button v-if="classEdit" class="primary" :disabled="busy || !classEdit.counselors.length">保存班级</button>
    </form>
  </section>

  <section class="panel" aria-labelledby="student-number-title">
    <div class="panel-heading"><h2 id="student-number-title">学号规则</h2></div>
    <form v-if="rule" class="fields management-form" @submit.prevent="saveRule">
      <p class="muted">登记和批量导入新生时校验学号位数与前缀。</p>
      <div class="two-col">
        <label>最短位数<input v-model.number="rule.minLength" type="number" min="1" max="32" required :disabled="busy"></label>
        <label>最长位数<input v-model.number="rule.maxLength" type="number" min="1" max="32" required :disabled="busy"></label>
      </div>
      <label>数字前缀<input v-model.trim="rule.prefix" inputmode="numeric" pattern="[0-9]{0,32}" maxlength="32"
        placeholder="可留空" :disabled="busy"></label>
      <button class="primary" :disabled="busy">保存规则</button>
    </form>
  </section>

  <section class="panel" :aria-busy="busy" aria-labelledby="edit-student-roster-title">
    <div class="panel-heading"><h2 id="edit-student-roster-title">学生名册</h2><button class="secondary" :disabled="busy" @click="refresh">刷新</button></div>
    <form class="filters roster-filters" @submit.prevent="run(() => loadStudents(1, { ...filter }))">
      <label>姓名或学号<input v-model.trim="filter.query" maxlength="80" :disabled="busy"></label>
      <label>班级<select v-model="filter.classId" :disabled="busy">
        <option :value="0">全部班级</option>
        <option v-for="group in classes" :key="group.id" :value="group.id">{{ group.name }}</option>
      </select></label>
      <div class="buttons"><button class="primary" :disabled="busy">查询</button></div>
    </form>
    <div v-if="students.items.length" class="table-panel">
      <table class="record-table">
        <thead><tr><th>学号</th><th>姓名</th><th>班级 / 辅导员</th><th>手机号</th><th>学籍有效期</th><th>账号绑定</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="student in students.items" :key="student.id">
            <td data-label="学号">{{ student.studentNo }}</td><td data-label="姓名">{{ student.name }}</td>
            <td data-label="班级 / 辅导员">{{ student.className }}<span class="cell-sub">{{ student.counselor }}</span></td>
            <td data-label="手机号">{{ student.phone || '未登记' }}</td>
            <td data-label="学籍有效期">{{ student.validUntil }}<span v-if="today && student.validUntil < today" class="cell-sub">已到期</span></td>
            <td data-label="账号绑定">{{ student.bound ? '已绑定' : '未绑定' }}</td>
            <td class="row-actions"><button class="text-button" :disabled="busy" @click="editStudent(student)">编辑</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else-if="ready && !busy" class="empty">暂无符合条件的学生</div>
    <div v-if="pageCount > 1" class="pager">
      <span>第 {{ page }} / {{ pageCount }} 页，共 {{ students.total }} 条</span>
      <button class="secondary" :disabled="busy || page <= 1" @click="run(() => loadStudents(page - 1))">上一页</button>
      <button class="secondary" :disabled="busy || page >= pageCount" @click="run(() => loadStudents(page + 1))">下一页</button>
    </div>
  </section>

  <dialog ref="editor" class="panel roster-editor" aria-labelledby="student-editor-title"
    @cancel="busy && $event.preventDefault()" @close="studentEdit = undefined">
    <template v-if="studentEdit">
      <div class="panel-heading"><h2 id="student-editor-title">编辑学籍 · {{ studentEdit.studentNo }}</h2></div>
      <form class="fields dialog-body" @submit.prevent="saveStudent">
        <p v-if="studentError" class="error" role="alert">{{ studentError }}</p>
        <div class="two-col">
          <label>姓名<input v-model.trim="studentEdit.name" maxlength="80" required :disabled="busy"></label>
          <label>手机号<input v-model.trim="studentEdit.phone" type="tel" inputmode="tel" pattern="1[0-9]{10}"
            placeholder="选填" :disabled="busy"></label>
          <label>班级<select v-model="studentEdit.classId" :disabled="busy" required>
            <option v-for="group in classes" :key="group.id" :value="group.id">{{ group.name }}</option>
          </select></label>
          <label>学籍有效期至<input v-model="studentEdit.validUntil" type="date" min="1000-01-01" max="9999-12-31" required :disabled="busy"></label>
        </div>
        <p v-if="studentEdit.bound && studentEdit.classId !== originalClass" class="muted">转班会撤销该学生原班级的班委身份。</p>
        <p v-if="!studentEdit.bound" class="muted">未注册的学生须使用更新后的信息注册。</p>
        <div class="buttons">
          <button class="primary" :disabled="busy">保存学籍</button>
          <button type="button" class="secondary" :disabled="busy" @click="editor?.close()">取消</button>
        </div>
      </form>
    </template>
  </dialog>
</template>
