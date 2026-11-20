<script setup lang="ts">
import { reactive, shallowRef } from 'vue'
import {
  createRole,
  renameRole,
  replaceGrants,
} from '../../api/generated/sdk.gen'
import type { RolesResponse } from '../../api/generated/types.gen'
import { useSession } from '../../session'
import RequestNotice from '../RequestNotice.vue'

type Role = RolesResponse['items'][number]
const props = defineProps<{ role: Role | null }>()
const emit = defineEmits<{ saved: [role: Role]; dismiss: [] }>()
const { data, secured } = useSession()
const failure = shallowRef<unknown>()
const busy = shallowRef(false)
const form = reactive({
  name: props.role?.name ?? '',
  code: props.role?.code ?? '',
})
const permissionOptions = [
  { permission: 'orders:read', label: '读取订单', limited: false },
  { permission: 'orders:create', label: '创建草稿', limited: true },
  { permission: 'orders:edit', label: '编辑草稿', limited: true },
  { permission: 'orders:submit', label: '提交订单', limited: true },
  { permission: 'orders:approve', label: '批准订单', limited: false },
  { permission: 'orders:cancel', label: '取消订单', limited: false },
]
const selection = reactive(
  Object.fromEntries(
    permissionOptions.map(({ permission }) => [
      permission,
      props.role?.grants.find((grant) => grant.permission === permission)
        ?.scope ?? '',
    ]),
  ),
)

async function perform(operation: () => Promise<Role>) {
  if (busy.value) return
  failure.value = undefined
  busy.value = true
  try {
    emit('saved', await operation())
  } catch (error) {
    failure.value = error
  } finally {
    busy.value = false
  }
}

function saveName() {
  const role = props.role
  const name = form.name
  const code = form.code.trim().toUpperCase()
  if (role)
    void perform(() =>
      data((headers) =>
        renameRole({
          headers,
          path: { id: role.id },
          body: { name },
        }),
      ),
    )
  else
    void perform(() =>
      data((headers) =>
        createRole({
          headers,
          body: { name, code },
        }),
      ),
    )
}

function saveGrants() {
  const role = props.role
  if (!role) return
  const grants = Object.entries(selection)
    .filter(([, scope]) => scope)
    .map(([permission, scope]) => ({ permission, scope }))
  void perform(async () => {
    await secured((headers) =>
      replaceGrants({ headers, path: { id: role.id }, body: { grants } }),
    )
    return { ...role, grants }
  })
}
</script>

<template>
  <div class="panel stack">
    <div class="actions editor-title">
      <h2>{{ role ? '编辑角色' : '新增角色' }}</h2>
      <button class="secondary" type="button" @click="emit('dismiss')">
        关闭
      </button>
    </div>
    <RequestNotice :failure="failure" />
    <form class="form-grid" aria-label="角色资料" @submit.prevent="saveName">
      <label
        >角色代码
        <input
          v-model="form.code"
          name="role-code"
          :readonly="!!role"
          maxlength="40"
          required
      /></label>
      <label
        >名称
        <input v-model="form.name" name="role-name" maxlength="100" required
      /></label>
      <button class="primary" type="submit" :disabled="busy">
        {{ role ? '保存名称' : '创建角色' }}
      </button>
    </form>
    <form
      v-if="role"
      class="stack"
      aria-label="动作授权"
      @submit.prevent="saveGrants"
    >
      <div class="form-grid">
        <label v-for="grant in permissionOptions" :key="grant.permission">
          {{ grant.label }}
          <select v-model="selection[grant.permission]">
            <option value="">未授予</option>
            <option value="OWN">本人</option>
            <option v-if="!grant.limited" value="DEPARTMENT">所在部门</option>
            <option v-if="!grant.limited" value="ALL">全局</option>
          </select>
        </label>
      </div>
      <div class="actions">
        <button class="secondary" type="submit" :disabled="busy">
          保存动作授权
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.editor-title {
  justify-content: space-between;
}
.editor-title h2 {
  margin: 0;
}
</style>
