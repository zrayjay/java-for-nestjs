<script setup lang="ts">
import { shallowRef } from 'vue'
import { roles } from '../../api/generated/sdk.gen'
import type { RolesResponse } from '../../api/generated/types.gen'
import { useSession } from '../../session'
import { usePaged } from '../../usePaged'
import PageControls from '../PageControls.vue'
import RequestNotice from '../RequestNotice.vue'
import RoleEditor from './RoleEditor.vue'

type Role = RolesResponse['items'][number]
const emit = defineEmits<{ changed: [] }>()
const { data } = useSession()
const { page, items, total, busy, failure, size, load } = usePaged(
  (page, size) => data((headers) => roles({ headers, query: { page, size } })),
)
const active = shallowRef<Role | null>()

function saved(role: Role) {
  active.value = role
  emit('changed')
  void load()
}
</script>

<template>
  <section class="stack" aria-labelledby="roles-title">
    <header class="page-header">
      <h1 id="roles-title">角色</h1>
      <button class="primary" type="button" @click="active = null">
        新增角色
      </button>
    </header>
    <RequestNotice :failure="failure" />
    <RoleEditor
      v-if="active !== undefined"
      :key="active?.id ?? 'new'"
      :role="active"
      @dismiss="active = undefined"
      @saved="saved"
    />
    <div class="panel">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>角色名称</th>
              <th>角色代码</th>
              <th>授权动作</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="role in items" :key="role.id">
              <td>{{ role.name }}</td>
              <td>
                <code>{{ role.code }}</code>
              </td>
              <td>{{ role.grants.length }}</td>
              <td>
                <button class="secondary" type="button" @click="active = role">
                  编辑
                </button>
              </td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="4">{{ busy ? '正在读取…' : '暂无角色' }}</td>
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
