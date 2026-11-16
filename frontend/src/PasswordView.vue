<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errorText } from './api'
import { maxPasswordLength, minPasswordLength, passwordPattern, passwordRule } from './passwordPolicy'
import type { Viewer } from './types'

defineProps<{ viewer: Viewer }>()
const emit = defineEmits<{ passwordChanged: [] }>()
const currentPassword = ref('')
const newPassword = ref('')
const confirmed = ref('')
const busy = ref(false)
const problem = ref('')

async function save() {
  if (busy.value) return
  problem.value = ''
  if (newPassword.value !== confirmed.value) {
    problem.value = '两次输入的新密码不一致'
    return
  }
  busy.value = true
  try {
    await api('/api/password', 'PUT', { currentPassword: currentPassword.value, newPassword: newPassword.value })
    ElMessage.success('密码已修改，请重新登录')
    emit('passwordChanged')
  } catch (e) { problem.value = errorText(e) }
  finally {
    currentPassword.value = ''
    newPassword.value = ''
    confirmed.value = ''
    busy.value = false
  }
}
</script>

<template>
  <header class="page-header"><h1>修改密码</h1></header>
  <section class="panel admin-card password-panel" aria-labelledby="password-settings-title">
    <h2 id="password-settings-title">设置新密码</h2>
    <p class="muted">{{ viewer.passwordChangeRequired ? '请先将临时密码更换为自己的密码。' : '保存后请使用新密码重新登录。' }}</p>
    <form class="fields" @submit.prevent="save">
      <input type="hidden" :value="viewer.username" autocomplete="username">
      <label>当前密码<input v-model="currentPassword" required type="password" maxlength="72" autocomplete="current-password" :disabled="busy"></label>
      <label>新密码
        <input v-model="newPassword" required type="password" :minlength="minPasswordLength" :maxlength="maxPasswordLength"
          :pattern="passwordPattern" :title="passwordRule" autocomplete="new-password" :disabled="busy">
        <small class="muted">{{ passwordRule }}</small>
      </label>
      <label>确认新密码<input v-model="confirmed" required type="password" :maxlength="maxPasswordLength"
        autocomplete="new-password" :disabled="busy"></label>
      <p v-if="problem" class="error" role="alert">{{ problem }}</p>
      <button class="primary" :disabled="busy">{{ busy ? '正在保存…' : '保存新密码' }}</button>
    </form>
  </section>
</template>
