<script setup lang="ts">
import LoginScreen from './components/LoginScreen.vue'
import WorkspaceShell from './components/WorkspaceShell.vue'
import { provideSession } from './session'

const { profile } = provideSession()
</script>

<template>
  <WorkspaceShell v-if="profile" />
  <LoginScreen v-else />
</template>
