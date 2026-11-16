<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { api, errorText } from './api'
import { maxEmailLength, studentNumberPattern } from './types'
import { maxPasswordLength, minPasswordLength, passwordPattern, passwordRule } from './passwordPolicy'
import type { Group } from './types'

const emit = defineEmits<{ signedIn: [] }>()
const binding = ref(false)
const busy = ref(false)
const sending = ref(false)
const cooldown = ref(0)
const signupForm = ref<HTMLFormElement>()
const codeLength = 6
let resendTimer: number | undefined
const problem = ref('')
const notice = ref('')
const username = ref('')
const password = ref('')
const groups = ref<Group[]>([])
const enrollment = ref({ studentNo: '', name: '', classId: 0, phone: '', email: '', emailCode: '', password: '' })

watch(() => [enrollment.value.studentNo, enrollment.value.name, enrollment.value.classId,
  enrollment.value.phone, enrollment.value.email], () => {
  enrollment.value.emailCode = ''
  if (binding.value) notice.value = ''
})

onUnmounted(() => window.clearInterval(resendTimer))

function startCooldown(seconds: number) {
  const endsAt = Date.now() + seconds * 1000
  cooldown.value = seconds
  window.clearInterval(resendTimer)
  resendTimer = window.setInterval(() => {
    cooldown.value = Math.max(0, Math.ceil((endsAt - Date.now()) / 1000))
    if (!cooldown.value) window.clearInterval(resendTimer)
  }, 250)
}

async function sendCode() {
  if (sending.value || busy.value || cooldown.value) return
  problem.value = ''
  notice.value = ''
  const { studentNo, name, classId, phone, email } = enrollment.value
  const fields = signupForm.value?.querySelectorAll<HTMLInputElement | HTMLSelectElement>('[data-roster-input]')
  for (const field of fields ?? []) {
    if (!field.reportValidity()) return
  }
  if (!classId) {
    problem.value = '请选择班级。'
    return
  }
  sending.value = true
  try {
    const result = await api<{ expiresInSeconds: number; resendAfterSeconds: number }>(
      '/api/bind/email-code', 'POST', { studentNo, name, classId, phone, email },
    )
    enrollment.value.emailCode = ''
    startCooldown(result.resendAfterSeconds)
    notice.value = '验证码已发送，请在 ' + Math.ceil(result.expiresInSeconds / 60) + ' 分钟内填写。'
  } catch (error) { problem.value = errorText(error) }
  finally { sending.value = false }
}

async function switchMode(value: boolean) {
  binding.value = value
  problem.value = ''
  notice.value = ''
  if (value && !groups.value.length) {
    try { groups.value = await api<Group[]>('/api/binding-classes') }
    catch (e) { problem.value = errorText(e) }
  }
}
async function submit() {
  if (sending.value || busy.value) return
  busy.value = true
  problem.value = ''
  notice.value = ''
  try {
    if (binding.value) {
      await api('/api/bind', 'POST', enrollment.value)
      username.value = enrollment.value.studentNo
      enrollment.value = { studentNo: '', name: '', classId: 0, phone: '', email: '', emailCode: '', password: '' }
      binding.value = false
      notice.value = '注册成功。请用学号和刚设置的密码登录。'
    } else {
      await api('/api/login', 'POST', new URLSearchParams({ username: username.value.trim(), password: password.value }))
      password.value = ''
      emit('signedIn')
    }
  } catch (e) { problem.value = errorText(e) }
  finally { busy.value = false }
}
</script>

<template>
  <main class="login-layout">
    <section class="login-intro">
      <div class="brand">
        <img src="/favicon.svg" alt="" width="48" height="48">
        <div><strong>山东建筑大学</strong><small>机电工程学院</small></div>
      </div>
      <div class="login-title">
        <p class="eyebrow">自律会 · 内部工作台</p>
        <h1>违纪记录<br>管理系统</h1>
        <p>统一登记、审核和查询学院违纪记录。</p>
      </div>
      <div class="login-footer">山东建筑大学机电工程学院自律会</div>
    </section>
    <section class="login-panel">
      <div class="login-form">
        <p class="eyebrow">账户服务</p>
        <h2>{{ binding ? '学生注册' : '登录工作台' }}</h2>
        <p class="muted">{{ binding ? '请填写与学生名册一致的学号、姓名和班级。名册已登记手机号时须填写。' : '学生使用学号，管理人员使用已分配的账号。' }}</p>
        <form ref="signupForm" class="fields" @submit.prevent="submit">
          <template v-if="binding">
            <label>学号<input v-model.trim="enrollment.studentNo" inputmode="numeric" :pattern="studentNumberPattern" required autocomplete="username" data-roster-input :disabled="sending || busy"></label>
            <label>姓名<input v-model.trim="enrollment.name" maxlength="80" required autocomplete="name" data-roster-input :disabled="sending || busy"></label>
            <label>班级<select v-model="enrollment.classId" required data-roster-input :disabled="sending || busy"><option :value="0" disabled>选择班级</option><option v-for="g in groups" :key="g.id" :value="g.id">{{ g.name }}</option></select></label>
            <label>学生手机号<input v-model.trim="enrollment.phone" type="tel" inputmode="tel" pattern="1[0-9]{10}"
              placeholder="名册未登记手机号时可留空" autocomplete="tel" data-roster-input :disabled="sending || busy"></label>
            <label>通知邮箱 <span class="required-mark" aria-hidden="true">*</span>
              <input v-model.trim="enrollment.email" type="email" :maxlength="maxEmailLength" required autocomplete="email" data-roster-input :disabled="sending || busy">
            </label>
            <div class="roster-field">
              <label for="registration-email-code">邮箱验证码 <span class="required-mark" aria-hidden="true">*</span></label>
              <div class="input-group">
                <input id="registration-email-code" v-model.trim="enrollment.emailCode" inputmode="numeric"
                  :pattern="'[0-9]{' + codeLength + '}'" :maxlength="codeLength" required autocomplete="one-time-code"
                  :disabled="sending || busy" placeholder="6 位数字">
                <button type="button" class="secondary" :disabled="sending || busy || cooldown > 0" @click="sendCode">
                  {{ sending ? '发送中…' : cooldown ? cooldown + ' 秒后重发' : '发送验证码' }}
                </button>
              </div>
            </div>
            <label>设置密码
              <input v-model="enrollment.password" type="password" :minlength="minPasswordLength" :maxlength="maxPasswordLength"
                :pattern="passwordPattern" :title="passwordRule" required autocomplete="new-password">
              <small class="muted">{{ passwordRule }}</small>
            </label>
          </template>
          <template v-else>
            <label>登录账号<input v-model.trim="username" placeholder="学号或登录名" required autocomplete="username"></label>
            <label>登录密码<input v-model="password" placeholder="输入密码" type="password" required autocomplete="current-password"></label>
          </template>
          <p v-if="problem" class="error" role="alert">{{ problem }}</p>
          <p v-if="notice" class="success" role="status">{{ notice }}</p>
          <button class="primary wide" :disabled="busy || sending">{{ busy ? '正在提交…' : binding ? '创建学生账号' : '登录' }}</button>
        </form>
        <button class="text-button sign-switch" :disabled="busy || sending" @click="switchMode(!binding)">{{ binding ? '返回登录' : '首次使用？注册学生账号' }}</button>
      </div>
    </section>
  </main>
</template>
