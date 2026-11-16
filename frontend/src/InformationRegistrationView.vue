<script setup lang="ts">
import { computed, onActivated, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorText } from './api'
import RosterImportPanel from './RosterImportPanel.vue'
import StudentDeletionPanel from './StudentDeletionPanel.vue'
import { studentNumberPattern } from './types'
import type { Group, Metadata, StudentNumberRule } from './types'

const groups = ref<Group[]>([])
const studentNumberRule = ref<StudentNumberRule>()
const enrollmentPattern = computed(() => {
  const rule = studentNumberRule.value
  if (!rule) return studentNumberPattern
  const minimum = Math.max(0, rule.minLength - rule.prefix.length)
  const maximum = rule.maxLength - rule.prefix.length
  return rule.prefix + '[0-9]{' + minimum + ',' + maximum + '}'
})
const enrollmentNumberHint = computed(() => {
  const rule = studentNumberRule.value
  if (!rule) return ''
  const length = rule.minLength === rule.maxLength ? String(rule.minLength) : rule.minLength + '–' + rule.maxLength
  return length + ' 位数字' + (rule.prefix ? '，以 ' + rule.prefix + ' 开头' : '')
})
const today = ref('')
const busy = ref(false)
const problem = ref('')
const student = reactive({ studentNo: '', name: '', classId: '' as number | '', phone: '', enrolledOn: '' })
async function run(task: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  problem.value = ''
  try { await task() }
  catch (e) { problem.value = errorText(e) }
  finally { busy.value = false }
}

onActivated(() => {
  void run(async () => {
    const [metadata, classes, rule] = await Promise.all([
      api<Pick<Metadata, 'today'>>('/api/metadata'),
      api<Group[]>('/api/admin/classes'),
      api<StudentNumberRule>('/api/admin/roster/student-number-rule'),
    ])
    today.value = metadata.today
    groups.value = classes
    studentNumberRule.value = rule
    if (!classes.some(item => item.id === student.classId)) student.classId = ''
  })
})
const addStudent = () => run(async () => {
  if (!student.classId) return
  await api('/api/admin/students', 'POST', student)
  Object.assign(student, { studentNo: '', name: '', phone: '' })
  ElMessage.success('已登记学生，学生可在登录页注册')
})
</script>

<template>
  <p v-if="problem" class="error" role="alert">{{ problem }}</p>
  <section class="panel" aria-labelledby="write-student-title">
    <div class="panel-heading"><h2 id="write-student-title">登记学生</h2></div>
    <form class="fields management-form" @submit.prevent="addStudent">
      <p class="muted">登记后，学生可凭名册信息注册账号。</p>
      <div class="two-col">
        <label><span>姓名 <span class="required-mark" aria-hidden="true">*</span></span>
          <input v-model.trim="student.name" maxlength="80" required :disabled="busy">
        </label>
        <label><span>学号 <span class="required-mark" aria-hidden="true">*</span></span>
          <input v-model.trim="student.studentNo" inputmode="numeric" :pattern="enrollmentPattern"
            :title="enrollmentNumberHint" required :disabled="busy">
          <small v-if="enrollmentNumberHint" class="muted">{{ enrollmentNumberHint }}</small>
        </label>
        <div class="roster-field">
          <div class="roster-label-row">
            <label for="registration-class"><span>班级 <span class="required-mark" aria-hidden="true">*</span></span></label>
            <RouterLink class="text-button" :to="{ name: 'admin', params: { section: 'classes' } }">新增班级</RouterLink>
          </div>
          <select id="registration-class" v-model="student.classId" required :disabled="busy">
            <option value="" disabled>请选择班级</option>
            <option v-for="g in groups" :key="g.id" :value="g.id">{{ g.name }}</option>
          </select>
        </div>
        <label>手机号
          <input v-model.trim="student.phone" type="tel" inputmode="tel" pattern="1[0-9]{10}" placeholder="选填" :disabled="busy">
        </label>
        <label><span>入学日期 <span class="required-mark" aria-hidden="true">*</span></span>
          <input v-model="student.enrolledOn" type="date" :max="today || undefined" required :disabled="busy">
        </label>
      </div>
      <button class="primary" :disabled="busy || !student.classId">登记学生</button>
    </form>
  </section>
  <RosterImportPanel :student-number-hint="enrollmentNumberHint" />
  <StudentDeletionPanel />
</template>
