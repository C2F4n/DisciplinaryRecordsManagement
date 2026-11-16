<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorText } from './api'
import { maxEmailLength } from './types'

defineOptions({ inheritAttrs: false })
const path = '/api/notification-email'
const email = ref('')
const loading = ref(false)
const loaded = ref(false)
const saving = ref(false)
const problem = ref('')
const success = ref('')

async function load() {
  loading.value = true
  problem.value = ''
  try {
    const settings = await api<{ email: string | null }>(path)
    email.value = settings.email ?? ''
    loaded.value = true
  } catch (error) { problem.value = errorText(error) }
  finally { loading.value = false }
}

async function save() {
  if (saving.value) return
  saving.value = true
  problem.value = ''
  success.value = ''
  try {
    await api(path, 'PUT', { email: email.value })
    success.value = '通知邮箱已保存。'
  } catch (error) { problem.value = errorText(error) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <header class="page-header"><h1>通知邮箱</h1></header>
  <section class="panel admin-card password-panel" aria-labelledby="email-settings-title">
    <h2 id="email-settings-title">收件地址</h2>
    <p class="muted">系统启用邮件后，新登记的违纪会发送提醒，详细信息可登录工作台查看。</p>
    <p v-if="loading" role="status">正在读取邮箱…</p>
    <p v-if="problem" class="error" role="alert">{{ problem }}</p>
    <p v-if="success" class="success" role="status">{{ success }}</p>
    <form v-if="loaded" class="fields" @submit.prevent="save">
      <label>收件邮箱
        <input v-model.trim="email" type="email" :maxlength="maxEmailLength" required autocomplete="email"
          :disabled="saving" @input="success = ''">
      </label>
      <button class="primary" :disabled="saving">{{ saving ? '正在保存…' : '保存邮箱' }}</button>
    </form>
    <button v-else-if="!loading" class="secondary" @click="load">重试</button>
  </section>
</template>
