<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, errorText } from './api'
import { roleNames } from './types'

defineOptions({ inheritAttrs: false })

type IdentityCount = { code: string; accountCount: number; maximum: number | null }
type Guide = { editDays: number; identities: IdentityCount[] }
type IdentityDescription = { code: string; title: string; permissions: string }

const guide = ref<Guide>()
const loading = ref(false)
const problem = ref('')
const collegePermissions = '查看全院违纪记录和统计，修改或删除记录，审核通过或驳回。可查看并下载月报，导出正式记录和通报表。'
const rows = computed(() => {
  const data = guide.value
  if (!data) return []
  const editWindow = '从违纪发生日起 ' + data.editDays + ' 个自然日内'
  const descriptions: IdentityDescription[] = [
    { code: 'CORE_ADMIN', title: '核心管理员',
      permissions: '登记、查看、审核和处理全院违纪记录，可修改、删除及按流程纠错。可查看统计、下载月报和导出，并管理名册、账号权限、系统设置和审计。' },
    { code: 'PRESIDENT', title: roleNames.PRESIDENT, permissions: collegePermissions },
    { code: 'TEACHER', title: roleNames.TEACHER, permissions: collegePermissions },
    { code: 'DISCIPLINE_HEAD', title: '自律管理部部长', permissions: collegePermissions },
    { code: 'DEPARTMENT_HEAD', title: '其他部门部长',
      permissions: '查看所属部门的违纪记录和统计，审核通过或驳回本部门待审记录。' + editWindow + '可修改或删除本部门记录。' },
    { code: 'OFFICER', title: roleNames.OFFICER,
      permissions: '查看所属部门的违纪记录。' + editWindow + '可登记、修改或删除本部门记录。' },
    { code: 'DISCIPLINE_OFFICE', title: roleNames.DISCIPLINE_OFFICE,
      permissions: '查看全院「已审核」记录，可核对后标记为「已交回」或「已申诉」。' },
    { code: 'QUALITY_HEAD', title: roleNames.QUALITY_HEAD, permissions: '查看并下载全院月报。' },
    { code: 'CLASS_COMMITTEE', title: roleNames.CLASS_COMMITTEE, permissions: '查看本人及所在班级的违纪记录和处理状态。' },
    { code: 'STUDENT', title: '学生', permissions: '查看本人的违纪记录和处理状态。每名学生可绑定 1 个账号。' },
    { code: 'UNASSIGNED', title: '未分配身份的员工账号', permissions: '可登录、修改本人密码、查看本人通知。' },
  ]
  const counts = new Map(data.identities.map(row => [row.code, row]))
  return descriptions.flatMap(description => {
    const count = counts.get(description.code)
    return count ? [{ ...description, ...count }] : []
  })
})

async function load() {
  loading.value = true
  problem.value = ''
  try { guide.value = await api<Guide>('/api/information/permissions') }
  catch (e) { problem.value = errorText(e) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page-header">
    <h1>权限说明</h1>
    <button class="secondary" :disabled="loading" @click="load">刷新</button>
  </div>
  <section class="panel table-panel" :aria-busy="loading" aria-label="身份、权限和账号数量">
    <div class="panel-heading"><h2>各身份的权限和账号数量</h2></div>
    <p class="panel-message muted">
      按各身份统计已分配的账号，包含停用账号，已移除名册的学生账号从统计中剔除。账号有多个身份时会分别计入，权限叠加。
    </p>
    <p v-if="problem" class="error panel-message" role="alert">{{ problem }}</p>
    <div v-if="loading && !guide" class="empty">正在读取账号数量…</div>
    <table v-if="guide" class="record-table permission-table">
      <thead><tr><th scope="col">身份</th><th scope="col">权限</th><th scope="col">账号数量</th><th scope="col">最大数量</th></tr></thead>
      <tbody>
        <tr v-for="item in rows" :key="item.code">
          <td data-label="身份"><strong>{{ item.title }}</strong></td>
          <td data-label="权限" class="permission-description">{{ item.permissions }}</td>
          <td data-label="账号数量" class="permission-count">{{ item.accountCount }}</td>
          <td data-label="最大数量" class="permission-count">{{ item.maximum ?? '未设固定上限' }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
