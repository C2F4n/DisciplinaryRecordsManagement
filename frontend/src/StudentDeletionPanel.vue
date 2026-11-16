<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorText } from './api'
import type { RosterStudent } from './types'

const emit = defineEmits<{ rosterChanged: [] }>()
const path = '/api/admin/roster/students'
const query = ref('')
const appliedQuery = ref('')
const rows = ref<RosterStudent[]>([])
const page = ref(1)
const total = ref(0)
const size = 10
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

async function load(index: number, term = appliedQuery.value) {
  const params = new URLSearchParams({ query: term, page: String(index), size: String(size) })
  const data = await api<{ items: RosterStudent[]; total: number }>(path + '?' + params)
  rows.value = data.items
  total.value = data.total
  appliedQuery.value = term
  page.value = index
}

async function remove(student: RosterStudent) {
  if (busy.value) return
  try {
    const message = student.bound
      ? '删除「' + student.name + ' · ' + student.studentNo + '」的名册信息并停用其登录？历史违纪和月报会保留。'
      : '删除「' + student.name + ' · ' + student.studentNo + '」的名册信息？删除后无法凭该名册信息注册。'
    await ElMessageBox.confirm(message, '删除学生', { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' })
  } catch { return }

  await run(async () => {
    await api(path + '/' + student.id + '?version=' + student.version, 'DELETE')
    const previousPage = rows.value.length === 1 && page.value > 1
    rows.value = rows.value.filter(row => row.id !== student.id)
    total.value -= 1
    ElMessage.success('学生已删除')
    emit('rosterChanged')
    await load(previousPage ? page.value - 1 : page.value)
  })
}
</script>

<template>
  <section class="panel" :aria-busy="busy" aria-labelledby="student-delete-title">
    <div class="panel-heading"><h2 id="student-delete-title">删除学生</h2></div>
    <form class="filters roster-filters" @submit.prevent="run(() => load(1, query))">
      <label><span>姓名或学号 <span class="required-mark" aria-hidden="true">*</span></span>
        <input v-model.trim="query" maxlength="80" required :disabled="busy">
      </label>
      <div class="buttons"><button class="primary" :disabled="busy || !query">查找学生</button></div>
    </form>
    <p v-if="problem" class="error panel-message" role="alert">{{ problem }}</p>
    <div v-if="rows.length" class="table-panel">
      <table class="record-table">
        <thead><tr><th>学号</th><th>姓名</th><th>班级</th><th>账号</th><th>操作</th></tr></thead>
        <tbody><tr v-for="student in rows" :key="student.id">
          <td data-label="学号">{{ student.studentNo }}</td>
          <td data-label="姓名">{{ student.name }}</td>
          <td data-label="班级">{{ student.className }}</td>
          <td data-label="账号">{{ student.bound ? '已绑定' : '未绑定' }}</td>
          <td class="row-actions"><button class="danger" :disabled="busy" @click="remove(student)">删除</button></td>
        </tr></tbody>
      </table>
    </div>
    <div v-else-if="appliedQuery && !busy" class="empty">未找到符合条件的学生</div>
    <div v-if="total > size" class="pager">
      <span>共 {{ total }} 条</span>
      <button class="secondary" :disabled="busy || page === 1" @click="run(() => load(page - 1))">上一页</button>
      <button class="secondary" :disabled="busy || page * size >= total" @click="run(() => load(page + 1))">下一页</button>
    </div>
  </section>
</template>
