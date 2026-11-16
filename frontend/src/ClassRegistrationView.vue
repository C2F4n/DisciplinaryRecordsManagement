<script setup lang="ts">
import { computed, onActivated, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errorText } from './api'
import CounselorSelect from './CounselorSelect.vue'
import RosterImportPanel from './RosterImportPanel.vue'
import { maxClassCounselors } from './types'
import type { Counselor, Group, ManagedClass } from './types'

const groups = ref<ManagedClass[]>([])
const classesPath = '/api/admin/roster/classes'
const counselorsPath = '/api/admin/counselors'
const counselors = ref<Counselor[]>([])
const counselorName = ref('')
const group = reactive({ name: '', counselors: [] as string[] })
const busy = ref(false)
const problem = ref('')
const page = ref(1)
const pageSize = 8
const last = computed(() => Math.max(1, Math.ceil(groups.value.length / pageSize)))
const visibleGroups = computed(() => groups.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(last, value => { if (page.value > value) page.value = value })

async function run(task: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  problem.value = ''
  try { await task() }
  catch (e) { problem.value = errorText(e) }
  finally { busy.value = false }
}

const load = () => run(async () => {
  const [classes, directory] = await Promise.all([api<ManagedClass[]>(classesPath), api<Counselor[]>(counselorsPath)])
  groups.value = classes
  counselors.value = directory
})
onActivated(load)

const saveClass = () => run(async () => {
  if (!group.counselors.length) return
  await api<Group>('/api/admin/classes', 'POST', group)
  groups.value = await api<ManagedClass[]>(classesPath)
  group.name = ''
  group.counselors = []
  ElMessage.success('已添加班级')
})

const saveCounselor = () => run(async () => {
  const counselor = await api<Counselor>(counselorsPath, 'POST', { name: counselorName.value })
  counselors.value.push(counselor)
  counselorName.value = ''
  ElMessage.success('辅导员已录入')
})

async function removeClass(group: ManagedClass) {
  if (busy.value || group.studentCount) return
  try {
    await ElMessageBox.confirm('删除「' + group.name + '」？', '删除班级',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  await run(async () => {
    await api(classesPath + '/' + group.id + '?version=' + group.version, 'DELETE')
    groups.value = groups.value.filter(item => item.id !== group.id)
    ElMessage.success('班级已删除')
  })
}
async function refreshClasses() {
  groups.value = await api<ManagedClass[]>(classesPath)
}

async function clearClassRoster(group: ManagedClass) {
  if (busy.value || !group.studentCount) return
  try {
    await ElMessageBox.confirm('删除「' + group.name + '」的全部 ' + group.studentCount
      + ' 名学生？已绑定账号会停用，历史违纪和月报会保留。', '清空班级学生',
    { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  await run(async () => {
    const params = new URLSearchParams({ version: String(group.version), studentCount: String(group.studentCount) })
    const count = await api<number>(classesPath + '/' + group.id + '/students?' + params, 'DELETE')
    ElMessage.success('已删除 ' + count + ' 名学生')
    await refreshClasses()
  })
}
</script>

<template>
  <p v-if="problem" class="error" role="alert">{{ problem }}</p>
  <section class="panel" aria-labelledby="write-class-title">
    <div class="panel-heading"><h2 id="write-class-title">添加班级</h2></div>
    <form class="fields management-form" @submit.prevent="saveClass">
      <label><span>班级名称 <span class="required-mark" aria-hidden="true">*</span></span>
        <input v-model.trim="group.name" required maxlength="100" :disabled="busy">
      </label>
      <label for="new-class-counselor">
        <span>辅导员 <span class="required-mark" aria-hidden="true">*</span></span>
        <CounselorSelect id="new-class-counselor" v-model="group.counselors" :options="counselors" :disabled="busy" />
        <small class="muted">最多选择 {{ maxClassCounselors }} 人</small>
      </label>
      <button class="primary" :disabled="busy || !group.counselors.length">添加班级</button>
    </form>
  </section>

  <section class="panel" aria-labelledby="write-counselor-title">
    <div class="panel-heading"><h2 id="write-counselor-title">录入辅导员</h2></div>
    <form class="fields management-form" @submit.prevent="saveCounselor">
      <label><span>辅导员姓名 <span class="required-mark" aria-hidden="true">*</span></span>
        <div class="input-group">
          <input v-model.trim="counselorName" required maxlength="80" :disabled="busy">
          <button class="secondary" :disabled="busy || !counselorName">录入</button>
        </div>
      </label>
    </form>
  </section>
  <RosterImportPanel mode="classes" @roster-changed="load" />
  <section class="panel" aria-labelledby="class-list-title" :aria-busy="busy">
    <div class="panel-heading">
      <h2 id="class-list-title">班级列表</h2><button class="secondary" :disabled="busy" @click="run(refreshClasses)">刷新班级</button>
    </div>
    <div v-if="groups.length" class="table-panel">
      <table class="record-table">
        <thead><tr><th>班级名称</th><th>辅导员</th><th>学生人数</th><th>操作</th></tr></thead>
        <tbody><tr v-for="g in visibleGroups" :key="g.id">
          <td data-label="班级名称">{{ g.name }}</td>
          <td data-label="辅导员">{{ g.counselor }}</td>
          <td data-label="学生人数">{{ g.studentCount }}</td>
          <td class="row-actions">
            <div class="buttons">
              <button v-if="g.studentCount" class="danger" :aria-label="'清空学生 ' + g.name"
                :disabled="busy" @click="clearClassRoster(g)">清空学生</button>
              <button class="danger" :aria-label="'删除班级 ' + g.name" :disabled="busy || g.studentCount > 0"
                :title="g.studentCount ? '请先处理班级内现有学生' : undefined" @click="removeClass(g)">删除班级</button>
            </div>
          </td>
        </tr></tbody>
      </table>
    </div>
    <p v-else-if="!busy && !problem" class="panel-message muted">暂无班级，可在上方添加或批量导入。</p>
    <div v-if="last > 1" class="pager">
      <button class="secondary" :disabled="busy || page <= 1" @click="page--">上一页</button>
      <span>{{ page }} / {{ last }}</span>
      <button class="secondary" :disabled="busy || page >= last" @click="page++">下一页</button>
    </div>
  </section>
</template>
