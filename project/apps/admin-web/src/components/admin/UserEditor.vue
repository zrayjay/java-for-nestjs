<script setup lang="ts">
import { reactive, shallowRef } from 'vue'
import {
  createUser,
  platformAdministrator,
  replacePassword,
  replaceRoles,
  update,
  user as getUser,
} from '../../api/generated/sdk.gen'
import type {
  DepartmentsResponse,
  RolesResponse,
  UserResponse,
} from '../../api/generated/types.gen'
import { useSession } from '../../session'
import RequestNotice from '../RequestNotice.vue'

const props = defineProps<{
  user: UserResponse | null
  departments: DepartmentsResponse
  roles: RolesResponse['items']
}>()
const emit = defineEmits<{ saved: [user?: UserResponse]; dismiss: [] }>()
const { data, secured } = useSession()
const failure = shallowRef<unknown>()
const busy = shallowRef(false)
const form = reactive({
  username: props.user?.username ?? '',
  displayName: props.user?.displayName ?? '',
  departmentId: props.user?.departmentId ?? props.departments[0]?.id ?? '',
  status: props.user?.status ?? 'ACTIVE',
  password: '',
  roleIds: [...(props.user?.roleIds ?? [])],
})

async function perform(operation: () => Promise<UserResponse | undefined>) {
  if (busy.value) return
  failure.value = undefined
  busy.value = true
  try {
    const result = await operation()
    form.password = ''
    emit('saved', result)
  } catch (error) {
    failure.value = error
  } finally {
    busy.value = false
  }
}

function saveProfile() {
  const current = props.user
  const { displayName, departmentId, status, password } = form
  const username = form.username.trim()
  if (current) {
    void perform(() =>
      data((headers) =>
        update({
          headers,
          path: { id: current.id },
          body: { displayName, departmentId, status },
        }),
      ),
    )
  } else {
    void perform(() =>
      data((headers) =>
        createUser({
          headers,
          body: { username, displayName, departmentId, password },
        }),
      ),
    )
  }
}

function saveRoles() {
  const current = props.user
  if (!current) return
  const roleIds = [...form.roleIds]
  void perform(async () => {
    await secured((headers) =>
      replaceRoles({
        headers,
        path: { id: current.id },
        body: { roleIds },
      }),
    )
    return data((headers) => getUser({ headers, path: { id: current.id } }))
  })
}

function savePassword() {
  const current = props.user
  if (!current) return
  const password = form.password
  void perform(async () => {
    await secured((headers) =>
      replacePassword({
        headers,
        path: { id: current.id },
        body: { password },
      }),
    )
    return current
  })
}

function toggleAdmin() {
  const current = props.user
  if (!current) return
  void perform(async () => {
    const enabled = !current.platformAdmin
    await secured((headers) =>
      platformAdministrator({
        headers,
        path: { id: current.id },
        body: { enabled },
      }),
    )
    return { ...current, platformAdmin: enabled }
  })
}
</script>

<template>
  <div class="panel stack user-editor">
    <div class="actions editor-title">
      <h2>{{ user ? '编辑用户' : '新增用户' }}</h2>
      <button type="button" class="secondary" @click="emit('dismiss')">
        关闭
      </button>
    </div>
    <RequestNotice :failure="failure" />
    <form class="form-grid" aria-label="用户资料" @submit.prevent="saveProfile">
      <label
        >用户名
        <input
          v-model="form.username"
          name="account-username"
          :readonly="!!user"
          required
      /></label>
      <label
        >姓名
        <input
          v-model="form.displayName"
          name="display-name"
          maxlength="100"
          required
      /></label>
      <label
        >部门
        <select v-model="form.departmentId" name="department" required>
          <option v-for="team in departments" :key="team.id" :value="team.id">
            {{ team.name }}
          </option>
        </select>
      </label>
      <label v-if="user"
        >状态
        <select v-model="form.status" name="account-status">
          <option value="ACTIVE">启用</option>
          <option value="DISABLED">停用</option>
        </select>
      </label>
      <label v-else
        >初始密码
        <input
          v-model="form.password"
          name="initial-password"
          type="password"
          minlength="12"
          autocomplete="new-password"
          required
      /></label>
      <button class="primary" type="submit" :disabled="busy">
        {{ user ? '保存资料' : '创建用户' }}
      </button>
    </form>
    <template v-if="user">
      <form class="stack" aria-label="用户角色" @submit.prevent="saveRoles">
        <fieldset>
          <legend>业务角色</legend>
          <label class="role" v-for="role in roles" :key="role.id">
            <input v-model="form.roleIds" type="checkbox" :value="role.id" />{{
              role.name
            }}
          </label>
          <p v-if="!roles.length" class="muted">暂无可分配角色。</p>
        </fieldset>
        <div class="actions">
          <button
            class="secondary"
            type="submit"
            :disabled="busy || !roles.length"
          >
            保存角色
          </button>
          <button
            class="secondary"
            type="button"
            :disabled="busy"
            @click="toggleAdmin"
          >
            {{ user.platformAdmin ? '撤销管理员权限' : '授予管理员权限' }}
          </button>
        </div>
      </form>
      <form
        class="form-grid password-form"
        aria-label="重置用户密码"
        @submit.prevent="savePassword"
      >
        <label
          >新密码
          <input
            v-model="form.password"
            name="new-password"
            type="password"
            minlength="12"
            autocomplete="new-password"
            required
        /></label>
        <button class="secondary" type="submit" :disabled="busy">
          重置密码
        </button>
      </form>
    </template>
  </div>
</template>

<style scoped>
.editor-title {
  justify-content: space-between;
}
.editor-title h2 {
  margin: 0;
}
fieldset {
  border: 1px solid var(--line);
  border-radius: 0.35rem;
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.8rem;
}
.role {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.role input {
  width: 1.1rem;
  height: 1.1rem;
}
.password-form {
  grid-template-columns: minmax(0, 21rem) auto;
  justify-content: start;
}
</style>
