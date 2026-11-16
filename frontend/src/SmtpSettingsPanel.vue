<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { api, ApiError, errorText } from './api'
import { maxEmailLength } from './types'
import { maxPasswordLength, minPasswordLength, passwordPattern, passwordRule } from './passwordPolicy'

type Transport = 'STARTTLS' | 'SSL' | 'NONE'
type Settings = {
  enabled: boolean
  host: string
  port: number
  username: string
  senderAddress: string
  authentication: boolean
  transport: Transport
  passwordConfigured: boolean
  version: number
  restartRequired: boolean
  active: boolean
  registrationEmailReady: boolean
}

const path = '/api/admin/smtp-settings'
const unlockHeader = 'X-SMTP-Unlock'
const lockedCode = 4033
const maxHostLength = 253
const maxSmtpPasswordLength = 512
const maxPort = 65_535
const token = ref('')
const settings = ref<Settings>()
const busy = ref(false)
const problem = ref('')
const success = ref('')
const gatePassword = ref('')
const mailPassword = ref('')
const passwords = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
let timeout: number | undefined
let disposed = false

function clear() {
  window.clearTimeout(timeout)
  token.value = ''
  settings.value = undefined
  gatePassword.value = ''
  mailPassword.value = ''
  Object.assign(passwords, { currentPassword: '', newPassword: '', confirmPassword: '' })
  problem.value = ''
  success.value = ''
}

function revoke(access: string) {
  if (access) void api(path + '/unlock', 'DELETE', undefined, { [unlockHeader]: access }).catch(() => {})
}

function lock() {
  const previous = token.value
  clear()
  revoke(previous)
}
onUnmounted(() => {
  disposed = true
  lock()
})

async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  problem.value = ''
  success.value = ''
  try { await action() }
  catch (error) {
    if (error instanceof ApiError && error.code === lockedCode) clear()
    problem.value = errorText(error)
  } finally { busy.value = false }
}

function unlock() {
  void run(async () => {
    try {
      const result = await api<{ token: string; settings: Settings; expiresInMillis: number }>(
        path + '/unlock', 'POST', { password: gatePassword.value },
      )
      if (disposed) {
        revoke(result.token)
        return
      }
      token.value = result.token
      settings.value = result.settings
      timeout = window.setTimeout(() => {
        lock()
        problem.value = '解锁已到期，请重新输入配置密码。'
      }, result.expiresInMillis)
    } finally { gatePassword.value = '' }
  })
}

function save() {
  const current = settings.value
  const access = token.value
  if (!current || !access) return
  void run(async () => {
    const { enabled, host, port, username, senderAddress, authentication, transport, version } = current
    const saved = await api<Settings>(path, 'PUT',
      { enabled, host, port, username, senderAddress, authentication, transport, version, password: mailPassword.value },
      { [unlockHeader]: access },
    )
    if (token.value !== access) return
    settings.value = saved
    mailPassword.value = ''
    success.value = '已保存。重启服务后使用新配置。'
  })
}

function changePassword() {
  void run(async () => {
    if (passwords.newPassword !== passwords.confirmPassword) {
      problem.value = '两次输入的新密码不一致。'
      return
    }
    await api(path + '/password', 'PUT',
      { currentPassword: passwords.currentPassword, newPassword: passwords.newPassword }, { [unlockHeader]: token.value })
    clear()
    success.value = '配置密码已修改，请使用新密码解锁。'
  })
}
</script>

<template>
  <div class="management-stack">
    <p v-if="problem" class="error" role="alert">{{ problem }}</p>
    <p v-if="success" class="success" role="status">{{ success }}</p>

    <section v-if="!settings" class="panel" aria-labelledby="smtp-unlock-title">
      <div class="panel-heading"><h2 id="smtp-unlock-title">解锁 SMTP 配置</h2></div>
      <form class="fields management-form smtp-unlock-form" @submit.prevent="unlock">
        <label>配置密码
          <input v-model="gatePassword" type="password" :maxlength="maxSmtpPasswordLength"
            required autocomplete="current-password" :disabled="busy">
        </label>
        <button class="primary" :disabled="busy">{{ busy ? '正在解锁…' : '解锁' }}</button>
      </form>
    </section>

    <template v-else>
      <section class="panel" aria-labelledby="smtp-settings-title">
        <div class="panel-heading">
          <div>
            <h2 id="smtp-settings-title">发件服务器</h2>
            <small class="muted">
              注册验证码{{ settings.registrationEmailReady ? '已配置' : '未配置' }}
              · 违纪邮件{{ settings.active ? '已启用' : '未启用' }}
              {{ settings.restartRequired ? ' · 已保存的变更待重启生效' : '' }}
            </small>
          </div>
          <button type="button" class="secondary" :disabled="busy" @click="lock">锁定配置</button>
        </div>
        <form class="fields management-form" @submit.prevent="save">
          <label class="smtp-checkbox"><input v-model="settings.enabled" type="checkbox" :disabled="busy">启用违纪邮件通知</label>
          <div class="two-col">
            <label>SMTP 服务器
              <input v-model.trim="settings.host" :maxlength="maxHostLength" :required="settings.enabled"
                placeholder="smtp.example.com" :disabled="busy">
            </label>
            <label>端口
              <input v-model.number="settings.port" type="number" min="1" :max="maxPort" required :disabled="busy">
            </label>
            <label>连接加密
              <select v-model="settings.transport" :disabled="busy">
                <option value="STARTTLS">STARTTLS（常用端口 587）</option>
                <option value="SSL">SSL/TLS（常用端口 465）</option>
                <option value="NONE">无加密</option>
              </select>
            </label>
            <label>发件邮箱
              <input v-model.trim="settings.senderAddress" type="email" :maxlength="maxEmailLength"
                :required="settings.enabled" :disabled="busy">
            </label>
          </div>
          <label class="smtp-checkbox"><input v-model="settings.authentication" type="checkbox" :disabled="busy">使用 SMTP 身份认证</label>
          <div v-if="settings.authentication" class="two-col">
            <label>SMTP 登录账号
              <input v-model.trim="settings.username" :maxlength="maxEmailLength" :required="settings.enabled" :disabled="busy">
            </label>
            <label>SMTP 授权码
              <input v-model="mailPassword" type="password" :maxlength="maxSmtpPasswordLength" autocomplete="new-password"
                :required="settings.enabled && !settings.passwordConfigured" :disabled="busy"
                :placeholder="settings.passwordConfigured ? '已配置，留空保留' : '填写授权码或应用专用密码'">
            </label>
          </div>
          <button class="primary" :disabled="busy">保存 SMTP 配置</button>
        </form>
      </section>

      <section class="panel" aria-labelledby="smtp-password-title">
        <div class="panel-heading"><h2 id="smtp-password-title">修改配置密码</h2></div>
        <form class="fields management-form smtp-unlock-form" @submit.prevent="changePassword">
          <label>当前配置密码
            <input v-model="passwords.currentPassword" type="password" :maxlength="maxSmtpPasswordLength"
              autocomplete="current-password" required :disabled="busy">
          </label>
          <label>新配置密码
            <input v-model="passwords.newPassword" type="password" autocomplete="new-password"
              :minlength="minPasswordLength" :maxlength="maxPasswordLength" :pattern="passwordPattern"
              :title="passwordRule" required :disabled="busy">
            <small class="muted">{{ passwordRule }}</small>
          </label>
          <label>确认新配置密码
            <input v-model="passwords.confirmPassword" type="password" autocomplete="new-password"
              :maxlength="maxPasswordLength" required :disabled="busy">
          </label>
          <button class="primary" :disabled="busy">修改配置密码</button>
        </form>
      </section>
    </template>
  </div>
</template>
