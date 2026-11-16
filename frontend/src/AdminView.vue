<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { adminSections } from './adminSections'
import InformationRegistrationView from './InformationRegistrationView.vue'
import ClassRegistrationView from './ClassRegistrationView.vue'
import RosterMaintenancePanel from './RosterMaintenancePanel.vue'
import AccountAccessView from './AccountAccessView.vue'
import type { Viewer } from './types'

defineOptions({ inheritAttrs: false })
defineProps<{ viewer: Viewer }>()

const route = useRoute()
const section = computed(() => adminSections.find(item => item.key === route.params.section) ?? adminSections[0])
</script>

<template>
  <header class="page-header"><h1>{{ section.label }}</h1></header>
  <div class="management-stack">
    <KeepAlive>
      <InformationRegistrationView v-if="section.key === 'students'" />
      <ClassRegistrationView v-else-if="section.key === 'classes'" />
      <RosterMaintenancePanel v-else-if="section.key === 'edit'" />
      <AccountAccessView v-else :viewer="viewer" />
    </KeepAlive>
  </div>
</template>
