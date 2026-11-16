<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { systemSections } from './adminSections'
import AuditPanel from './AuditPanel.vue'
import ExportTemplatesPanel from './ExportTemplatesPanel.vue'
import RetentionPanel from './RetentionPanel.vue'
import SmtpSettingsPanel from './SmtpSettingsPanel.vue'
import WorkflowPanel from './WorkflowPanel.vue'

defineOptions({ inheritAttrs: false })
const route = useRoute()
const section = computed(() => systemSections.find(item => item.key === route.params.section) ?? systemSections[0])
</script>

<template>
  <div class="page-header"><h1>{{ section.label }}</h1></div>
  <ExportTemplatesPanel v-if="section.key === 'templates'" />
  <RetentionPanel v-else-if="section.key === 'retention'" />
  <AuditPanel v-else-if="section.key === 'audit'" />
  <SmtpSettingsPanel v-else-if="section.key === 'smtp'" />
  <WorkflowPanel v-else :key="section.key" :section="section.key" />
</template>
