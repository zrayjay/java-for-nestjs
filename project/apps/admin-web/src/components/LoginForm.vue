<script setup lang="ts">
import { reactive } from 'vue'
import type { ApiError } from '../api/http'

defineProps<{ pending: boolean; error: ApiError | null }>()
const emit = defineEmits<{
  submit: [credentials: { username: string; password: string }]
}>()
const fields = reactive({ username: '', password: '' })

function submit() {
  emit('submit', {
    username: fields.username.trim(),
    password: fields.password,
  })
}
</script>

<template>
  <form class="login-form" @submit.prevent="submit">
    <label>
      用户名
      <input
        v-model="fields.username"
        name="username"
        autocomplete="username"
        required
      />
    </label>
    <label>
      密码
      <input
        v-model="fields.password"
        name="password"
        type="password"
        autocomplete="current-password"
        required
      />
    </label>
    <p v-if="error" class="error" role="alert">
      {{ error.message }}
      <small v-if="error.requestId">请求 ID {{ error.requestId }}</small>
    </p>
    <button class="primary" type="submit" :disabled="pending">
      {{ pending ? '正在登录…' : '登录' }}
      <span aria-hidden="true">↗</span>
    </button>
  </form>
</template>

<style scoped>
.login-form {
  display: grid;
  gap: 1.2rem;
  max-width: 23rem;
}

.primary {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
