<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElOption, ElSelect } from 'element-plus'
import { pinyin } from 'pinyin-pro'
import 'element-plus/es/components/select/style/css'
import { maxClassCounselors } from './types'
import type { Counselor } from './types'

const props = defineProps<{ options: Counselor[]; id: string; disabled?: boolean }>()
const selected = defineModel<string[]>({ required: true })
const search = ref('')
const alphabet = new Intl.Collator('en', { sensitivity: 'base' })

function searchKey(value: string) {
  return value.normalize('NFKC').normalize('NFD').replace(/[\s\p{M}]/gu, '').toLowerCase()
}

const indexed = computed(() => props.options.map(option => {
  const syllables = pinyin(option.name, { surname: 'head', toneType: 'none', type: 'array' })
  return {
    ...option,
    nameKey: searchKey(option.name),
    fullPinyin: searchKey(syllables.join('')),
    initials: searchKey(syllables.map(syllable => syllable[0] ?? '').join('')),
  }
}).sort((a, b) => alphabet.compare(a.fullPinyin, b.fullPinyin) || a.name.localeCompare(b.name, 'zh-CN')))

const matching = computed(() => {
  const query = searchKey(search.value)
  return indexed.value.filter(option =>
    option.nameKey.includes(query) || option.fullPinyin.includes(query) || option.initials.includes(query))
})
</script>

<template>
  <ElSelect :id="id" v-model="selected" class="counselor-select" filterable multiple :multiple-limit="maxClassCounselors"
    aria-label="辅导员" aria-required="true"
    :disabled="disabled || !options.length"
    :placeholder="options.length ? '输入姓名、拼音或首字母' : '请先录入辅导员'"
    :filter-method="(value: string) => { search = value }"
    no-match-text="没有匹配的辅导员" @visible-change="open => { if (!open) search = '' }">
    <ElOption v-for="option in matching" :key="option.id" :label="option.name" :value="option.name" />
  </ElSelect>
</template>
