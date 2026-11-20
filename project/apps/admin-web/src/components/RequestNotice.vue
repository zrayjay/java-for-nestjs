<script setup lang="ts">
import { ApiError, errorMessage } from '../api/http'

defineProps<{ failure: unknown }>()
</script>

<template>
  <p v-if="failure" class="error notice" role="alert">
    {{ errorMessage(failure) }}
    <small v-if="failure instanceof ApiError && failure.requestId"
      >请求 ID {{ failure.requestId }}</small
    >
  </p>
</template>

<style scoped>
.notice {
  padding: 0.65rem 0.9rem;
  border-left: 3px solid var(--danger);
  background: #fff0e5;
}
</style>
