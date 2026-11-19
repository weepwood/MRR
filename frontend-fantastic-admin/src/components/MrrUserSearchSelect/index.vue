<script setup lang="ts">
/**
 * 可搜索的用户选择框：输入关键字远端检索已存在用户（/api/v1/auth/users），
 * 选中后把用户名写回 v-model。
 *
 * 设计取舍：
 * - `remote + filterable`：输入即搜，选项随关键字变化，覆盖用户量增长的场景；
 * - `allow-create`：保留原文本输入框的超集能力——已删除/停用用户的用户名
 *   仍可手动输入检索日志，不受下拉选项限制；
 * - 检索失败静默清空选项（不阻断筛选），手输路径始终可用。
 */
import { onMounted, ref } from 'vue'
import apiUser from '@/api/modules/user'

interface UserOption {
  username: string
  displayName: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  id?: string
  placeholder?: string
}>(), {
  id: undefined,
  placeholder: '输入搜索用户名',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const options = ref<UserOption[]>([])
const loading = ref(false)

async function searchUsers(keyword: string) {
  loading.value = true
  try {
    const response = await apiUser.getUsers({
      keyword: keyword.trim() || undefined,
      size: 50,
    })
    const list = Array.isArray(response.data?.list) ? response.data.list : []
    options.value = list
      .filter(user => Boolean(user.username))
      .map(user => ({ username: String(user.username), displayName: String(user.displayName || '') }))
  }
  catch {
    // 拉取失败不阻断筛选：清空选项，allow-create 仍可手输。
    options.value = []
  }
  finally {
    loading.value = false
  }
}

function handleChange(value: unknown) {
  emit('update:modelValue', typeof value === 'string' ? value : '')
}

onMounted(() => {
  void searchUsers('')
})
</script>

<template>
  <el-select
    :id="props.id"
    class="mrr-user-search-select"
    :model-value="props.modelValue"
    filterable
    remote
    reserve-keyword
    clearable
    allow-create
    default-first-option
    :remote-method="searchUsers"
    :loading="loading"
    :placeholder="props.placeholder"
    @update:model-value="handleChange"
  >
    <el-option
      v-for="option in options"
      :key="option.username"
      :label="option.displayName ? `${option.username}（${option.displayName}）` : option.username"
      :value="option.username"
    />
  </el-select>
</template>

<style scoped>
.mrr-user-search-select {
  width: 100%;
}
</style>
