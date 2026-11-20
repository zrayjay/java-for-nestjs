<script setup lang="ts">
import { onMounted, shallowRef } from 'vue'
import { departments, roles, users } from '../../api/generated/sdk.gen'
import type {
  DepartmentsResponse,
  UserResponse,
  RolesResponse,
} from '../../api/generated/types.gen'
import { useSession } from '../../session'
import { usePaged } from '../../usePaged'
import PageControls from '../PageControls.vue'
import RequestNotice from '../RequestNotice.vue'
import UserEditor from './UserEditor.vue'

const emit = defineEmits<{ changed: [] }>()
const { data } = useSession()
const { page, items, total, busy, failure, size, load } = usePaged(
  (page, size) => data((headers) => users({ headers, query: { page, size } })),
)
const active = shallowRef<UserResponse | null>()
const catalog = shallowRef<DepartmentsResponse>([])
const roleOptions = shallowRef<RolesResponse['items']>([])
const metadataProblem = shallowRef<unknown>()

async function allRoles() {
  const collected: RolesResponse['items'] = []
  let index = 1
  while (true) {
    const result = await data((headers) =>
      roles({ headers, query: { page: index, size: 100 } }),
    )
    collected.push(...result.items)
    if (!result.items.length || collected.length >= result.total)
      return collected
    index++
  }
}

async function loadMetadata() {
  metadataProblem.value = undefined
  try {
    const [teams, options] = await Promise.all([
      data((headers) => departments({ headers })),
      allRoles(),
    ])
    catalog.value = teams
    roleOptions.value = options
  } catch (error) {
    metadataProblem.value = error
  }
}

onMounted(loadMetadata)

function departmentName(id: string) {
  return catalog.value.find((team) => team.id === id)?.name ?? id
}

function saved(user?: UserResponse) {
  active.value = user
  emit('changed')
  void load()
}
</script>

<template>
  <section class="stack" aria-labelledby="users-title">
    <header class="page-header">
      <h1 id="users-title">用户</h1>
      <button
        type="button"
        class="primary"
        :disabled="!catalog.length"
        @click="active = null"
      >
        新增用户
      </button>
    </header>
    <RequestNotice :failure="failure || metadataProblem" />
    <button
      v-if="metadataProblem"
      type="button"
      class="secondary"
      @click="loadMetadata"
    >
      重新读取部门与角色
    </button>
    <UserEditor
      v-if="active !== undefined"
      :key="active?.id ?? 'new'"
      :user="active"
      :departments="catalog"
      :roles="roleOptions"
      @dismiss="active = undefined"
      @saved="saved"
    />
    <div class="panel">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>姓名</th>
              <th>用户名</th>
              <th>部门</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in items" :key="user.id">
              <td>{{ user.displayName }}</td>
              <td>{{ user.username }}</td>
              <td>{{ departmentName(user.departmentId) }}</td>
              <td>
                {{ user.status === 'ACTIVE' ? '启用' : '停用'
                }}<span v-if="user.platformAdmin"> · 管理员</span>
              </td>
              <td>
                <button class="secondary" type="button" @click="active = user">
                  编辑
                </button>
              </td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="5">{{ busy ? '正在读取…' : '暂无用户' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PageControls
        :page="page"
        :total="total"
        :size="size"
        :pending="busy"
        @change="page = $event"
      />
    </div>
  </section>
</template>
