<script setup lang="ts">
import { computed, reactive, shallowRef } from 'vue'
import { createMenu, deleteMenu, updateMenu } from '../../api/generated/sdk.gen'
import type { MenusResponse } from '../../api/generated/types.gen'
import { useSession } from '../../session'
import RequestNotice from '../RequestNotice.vue'

type Menu = MenusResponse[number]
const props = defineProps<{ menu: Menu | null; menus: MenusResponse }>()
const emit = defineEmits<{ saved: [menu?: Menu]; dismiss: [] }>()
const { data, secured } = useSession()
const failure = shallowRef<unknown>()
const busy = shallowRef(false)
const form = reactive({
  label: props.menu?.label ?? '',
  parentId: props.menu?.parentId ?? '',
  routeKey: props.menu?.routeKey ?? '',
  permission: props.menu?.permission ?? '',
  sortOrder: props.menu?.sortOrder ?? 100,
})
const routeOptions = [
  { key: 'orders', label: '订单', permission: 'orders:read' },
  { key: 'users', label: '用户', permission: 'system:admin' },
  { key: 'roles', label: '角色', permission: 'system:admin' },
  { key: 'menus', label: '菜单', permission: 'system:admin' },
  { key: 'audit', label: '审计', permission: 'system:admin' },
]
const parentOptions = computed(() =>
  props.menus.filter(({ id }) => id !== props.menu?.id),
)
const required = computed(
  () => routeOptions.find(({ key }) => key === form.routeKey)?.permission,
)

async function perform(operation: () => Promise<Menu | undefined>) {
  if (busy.value) return
  busy.value = true
  failure.value = undefined
  try {
    emit('saved', await operation())
  } catch (error) {
    failure.value = error
  } finally {
    busy.value = false
  }
}

function save() {
  const body = {
    label: form.label,
    parentId: form.parentId || null,
    routeKey: form.routeKey || null,
    permission: required.value ?? (form.permission || null),
    sortOrder: form.sortOrder,
  }
  const menu = props.menu
  if (menu)
    void perform(() =>
      data((headers) => updateMenu({ headers, path: { id: menu.id }, body })),
    )
  else void perform(() => data((headers) => createMenu({ headers, body })))
}

function remove() {
  const menu = props.menu
  if (!menu || !window.confirm(`删除菜单「${menu.label}」？`)) return
  void perform(async () => {
    await secured((headers) => deleteMenu({ headers, path: { id: menu.id } }))
    return undefined
  })
}
</script>

<template>
  <div class="panel stack">
    <div class="actions editor-title">
      <h2>{{ menu ? '编辑菜单' : '新增菜单' }}</h2>
      <button class="secondary" type="button" @click="emit('dismiss')">
        关闭
      </button>
    </div>
    <RequestNotice :failure="failure" />
    <form class="stack" aria-label="菜单资料" @submit.prevent="save">
      <div class="form-grid">
        <label
          >显示名称
          <input
            v-model="form.label"
            name="menu-label"
            maxlength="100"
            required
        /></label>
        <label
          >上级菜单
          <select v-model="form.parentId">
            <option value="">根层</option>
            <option
              v-for="parent in parentOptions"
              :key="parent.id"
              :value="parent.id"
            >
              {{ parent.label }}
            </option>
          </select>
        </label>
        <label
          >页面路由
          <select v-model="form.routeKey">
            <option value="">分组</option>
            <option
              v-for="route in routeOptions"
              :key="route.key"
              :value="route.key"
            >
              {{ route.label }}
            </option>
          </select>
        </label>
        <label v-if="required"
          >所需权限 <input :value="required" readonly
        /></label>
        <label v-else
          >所需权限
          <select v-model="form.permission">
            <option value="">不限</option>
            <option value="system:admin">管理员</option>
            <option value="orders:read">读取订单</option>
            <option value="orders:create">创建草稿</option>
            <option value="orders:edit">编辑草稿</option>
            <option value="orders:submit">提交订单</option>
            <option value="orders:approve">批准订单</option>
            <option value="orders:cancel">取消订单</option>
          </select>
        </label>
        <label
          >排序值
          <input
            v-model.number="form.sortOrder"
            type="number"
            min="0"
            max="2147483647"
            required
        /></label>
      </div>
      <div class="actions">
        <button class="primary" type="submit" :disabled="busy">保存菜单</button>
        <button
          v-if="menu"
          class="danger"
          type="button"
          :disabled="busy"
          @click="remove"
        >
          删除菜单
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
