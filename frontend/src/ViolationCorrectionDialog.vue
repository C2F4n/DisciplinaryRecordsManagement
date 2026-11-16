<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, errorText } from './api'
import { stateNames, studentNumberPattern } from './types'
import type { Entry, Metadata, State, Student } from './types'

const props = defineProps<{ record: Entry; metadata: Metadata }>()
const emit = defineEmits<{ cancel: []; saved: [record: Entry] }>()
const original = props.record
const editor = ref<HTMLDialogElement>()
const search = ref('')
const student = ref<Student>()
const occurredOn = ref(original.occurredOn)
const typeCode = ref(original.typeCode)
const status = ref<State>(original.status)
const reason = ref('')
const searching = ref(false)
const saving = ref(false)
const problem = ref('')
const resolved = original.status === 'HANDED_BACK' || original.status === 'APPEALED'
const statusOptions = computed<State[]>(() => {
  if (!resolved) return [original.status]
  return student.value ? ['APPROVED'] : ['APPROVED', 'HANDED_BACK', 'APPEALED']
})
const changed = computed(() => !!student.value || occurredOn.value !== original.occurredOn
  || typeCode.value !== original.typeCode || status.value !== original.status)

onMounted(() => editor.value?.showModal())

function clearStudent() {
  if (student.value && resolved) status.value = original.status
  student.value = undefined
  problem.value = ''
}

async function lookup() {
  if (!search.value || searching.value || saving.value) return
  const number = search.value
  searching.value = true
  clearStudent()
  try {
    const result = await api<Student[]>('/api/students/lookup?' + new URLSearchParams({ q: number }))
    const candidate = result.find(item => item.studentNo === number)
    if (!candidate) { problem.value = '未找到使用该学号的有效绑定账号'; return }
    if (number === original.studentNo) { problem.value = '该学号就是当前记录的学生'; return }
    student.value = candidate
    if (resolved) status.value = 'APPROVED'
  } catch (e) { problem.value = errorText(e) }
  finally { searching.value = false }
}

async function save() {
  if (!changed.value || saving.value || searching.value) return
  if (search.value && !student.value) { problem.value = '请核对新学号，或清空学号以保留原学生'; return }
  saving.value = true
  problem.value = ''
  try {
    const updated = await api<Entry>('/api/admin/violations/' + original.id + '/correction', 'PUT', {
      version: original.version,
      studentNo: student.value?.studentNo ?? original.studentNo,
      occurredOn: occurredOn.value,
      typeCode: typeCode.value,
      status: status.value,
      correctionReason: reason.value,
    })
    emit('saved', updated)
  } catch (e) { problem.value = errorText(e) }
  finally { saving.value = false }
}

function cancel(event?: Event) {
  if (saving.value) { event?.preventDefault(); return }
  emit('cancel')
}
</script>

<template>
  <dialog ref="editor" class="panel roster-editor" aria-labelledby="correction-title" @cancel="cancel">
    <div class="panel-heading"><h2 id="correction-title">违纪记录纠错</h2></div>
    <form class="fields dialog-body" @submit.prevent="save">
      <p class="muted">当前学生 {{ original.studentName }} · {{ original.studentNo }} · {{ original.className }}</p>
      <label>更换学生（选填）
        <div class="input-group">
          <input v-model.trim="search" inputmode="numeric" maxlength="32" :pattern="studentNumberPattern"
            placeholder="输入新学生完整学号" :disabled="searching || saving"
            @input="clearStudent" @keydown.enter.prevent="lookup">
          <button type="button" class="secondary" :disabled="!search || searching || saving" @click="lookup">核对</button>
        </div>
      </label>
      <div v-if="student" class="candidate">
        <strong>{{ student.name }} · {{ student.studentNo }}</strong><span>{{ student.className }} / {{ student.counselor }}</span>
      </div>
      <div class="two-col">
        <label>发生日期<input v-model="occurredOn" type="date" :max="metadata.today" required :disabled="saving"></label>
        <label>违纪类型<select v-model="typeCode" :disabled="saving" required>
          <option v-for="type in metadata.types" :key="type.code" :value="type.code">{{ type.name }}</option>
        </select></label>
      </div>
      <label v-if="resolved">处理状态<select v-model="status" :disabled="saving">
        <option v-for="state in statusOptions" :key="state" :value="state">{{ stateNames[state] }}</option>
      </select></label>
      <p v-if="resolved && student" class="muted">更换学生后恢复为已审核，由办公室重新核对。</p>
      <label>纠错原因<textarea v-model.trim="reason" maxlength="250" rows="3" required :disabled="saving"></textarea></label>
      <p v-if="problem" class="error" role="alert">{{ problem }}</p>
      <div class="buttons">
        <button class="primary" :disabled="!changed || searching || saving">{{ saving ? '正在保存…' : '保存纠错' }}</button>
        <button type="button" class="secondary" :disabled="saving" @click="cancel()">取消</button>
      </div>
    </form>
  </dialog>
</template>
