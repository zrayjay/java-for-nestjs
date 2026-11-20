<script setup lang="ts">
import { computed, onMounted, shallowRef } from 'vue'
import { menus } from '../../api/generated/sdk.gen'
import type { MenusResponse } from '../../api/generated/types.gen'
import { useSession } from '../../session'
import RequestNotice from '../RequestNotice.vue'
import MenuEditor from './MenuEditor.vue'

const emit = defineEmits<{ changed: [] }>()
const { data } = useSession()
const entries = shallowRef<MenusResponse>([])
const active = shallowRef<MenusResponse[number] | null>()
const busy = shallowRef(false)
const failure = shallowRef<unknown>()
const names = computed(
  () => new Map(entries.value.map((menu) => [menu.id, menu.label])),
)

async function load() {
  busy.value = true
  failure.value = undefined
  try {
    entries.value = await data((headers) => menus({ headers }))
  } catch (error) {
    failure.value = error
  } finally {
    busy.value = false
  }
}

function saved(menu?: MenusResponse[number]) {
  active.value = menu
  emit('changed')
  void load()
}

onMounted(load)
</script>

<template>
  <section class="stack" aria-labelledby="menus-title">
    <header class="page-header">
      <h1 id="menus-title">菜单</h1>
      <button class="primary" type="button" @click="active = null">
        新增菜单
      </button>
    </header>
    <RequestNotice :failure="failure" />
    <MenuEditor
      v-if="active !== undefined"
      :key="active?.id ?? 'new'"
      :menu="active"
      :menus="entries"
      @saved="saved"
      @dismiss="active = undefined"
    />
    <div class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>菜单名称</th>
            <th>上级</th>
            <th>路由</th>
            <th>所需权限</th>
            <th>顺序</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in entries" :key="item.id">
            <td>{{ item.label }}</td>
            <td>{{ item.parentId ? names.get(item.parentId) : '根层' }}</td>
            <td>{{ item.routeKey || '分组' }}</td>
            <td>{{ item.permission || '不限' }}</td>
            <td>{{ item.sortOrder }}</td>
            <td>
              <button class="secondary" type="button" @click="active = item">
                编辑
              </button>
            </td>
          </tr>
          <tr v-if="!entries.length">
            <td colspan="6">{{ busy ? '正在读取…' : '暂无菜单' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
