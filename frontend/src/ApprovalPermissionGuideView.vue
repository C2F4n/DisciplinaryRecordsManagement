<script setup lang="ts">
defineOptions({ inheritAttrs: false })

const rules = [
  {
    role: '核心管理员',
    scope: '全院所有开放申请的身份，包括班委、部门干事、部门部长、团委办公室核对、素拓中心部长和主席。',
  },
  {
    role: '主席、团委老师',
    scope: '全院的班委和部门干事申请。',
  },
  {
    role: '部门部长（含自律管理部部长）',
    scope: '本人负责部门的干事申请。',
  },
  {
    role: '其他身份',
    scope: '无权限申请审批权。',
  },
]
</script>

<template>
  <div class="page-header">
    <div>
      <h1>审批权限</h1>
      <p class="muted">各身份可以批准或驳回的权限申请范围</p>
    </div>
  </div>
  <section class="panel table-panel" aria-label="权限申请审批范围">
    <div class="panel-heading"><h2>各身份的审批范围</h2></div>
    <table class="record-table permission-table">
      <thead><tr><th scope="col">审批身份</th><th scope="col">可审批的申请</th></tr></thead>
      <tbody>
        <tr v-for="rule in rules" :key="rule.role">
          <td data-label="审批身份" class="permission-description"><strong>{{ rule.role }}</strong></td>
          <td data-label="可审批的申请" class="permission-description">{{ rule.scope }}</td>
        </tr>
      </tbody>
    </table>
    <p class="panel-message muted">账号有多个身份时，审批范围取各身份的并集。审批人不能处理自己提交的申请。团委老师身份由核心管理员直接分配。</p>
  </section>
</template>
