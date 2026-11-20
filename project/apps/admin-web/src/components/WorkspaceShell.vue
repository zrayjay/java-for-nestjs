<script setup lang="ts">
import { computed, onMounted, onUnmounted, shallowRef } from 'vue'
import type { CurrentMenusResponse } from '../api/generated/types.gen'
import { useSession } from '../session'
import AuditView from './admin/AuditView.vue'
import MenusView from './admin/MenusView.vue'
import RolesView from './admin/RolesView.vue'
import UsersView from './admin/UsersView.vue'
import RequestNotice from './RequestNotice.vue'

const { menus, profile, pending, signOut, sync } = useSession()
const failure = shallowRef<unknown>()
const hash = shallowRef(location.hash.slice(1))
const pages = {
  users: UsersView,
  roles: RolesView,
  menus: MenusView,
  audit: AuditView,
}
type PageKey = keyof typeof pages | 'orders'
type MenuLink = { key: PageKey; label: string; depth: number }

function links(nodes: CurrentMenusResponse, depth = 0): MenuLink[] {
  return nodes.flatMap((node) => {
    const key = node.routeKey
    const permitted =
      key === 'orders'
        ? profile.value?.permissions.includes('orders:read')
        : profile.value?.permissions.includes('system:admin')
    const known =
      key === 'orders' || (key !== null && Object.hasOwn(pages, key))
    const own =
      key && known && permitted
        ? [{ key: key as PageKey, label: node.label, depth }]
        : []
    return [...own, ...links(node.children, depth + 1)]
  })
}

const navigation = computed(() => links(menus.value))
const selected = computed(
  () =>
    navigation.value.find((link) => link.key === hash.value) ??
    navigation.value[0],
)

function syncNavigation() {
  failure.value = undefined
  void sync().catch((reason: unknown) => {
    failure.value = reason
  })
}

function navigate() {
  hash.value = location.hash.slice(1)
  syncNavigation()
}

onMounted(() => {
  window.addEventListener('hashchange', navigate)
  window.addEventListener('focus', syncNavigation)
})
onUnmounted(() => {
  window.removeEventListener('hashchange', navigate)
  window.removeEventListener('focus', syncNavigation)
})
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <div class="brand"><span aria-hidden="true"></span>订单管理</div>
      <nav class="navigation" aria-label="工作台">
        <a
          v-for="link in navigation"
          :key="link.key"
          :href="`#${link.key}`"
          :aria-current="selected?.key === link.key ? 'page' : undefined"
          :style="{ paddingInlineStart: `${0.9 + link.depth * 0.45}rem` }"
        >
          {{ link.label }}
        </a>
      </nav>
      <div class="identity">
        <strong>{{ profile?.displayName }}</strong>
        <small>{{ profile?.username }}</small>
        <button type="button" :disabled="pending" @click="signOut">
          退出登录
        </button>
      </div>
    </aside>
    <main class="workspace-main">
      <RequestNotice :failure="failure" />
      <component
        v-if="selected && selected.key !== 'orders'"
        :is="pages[selected.key]"
        :key="selected.key"
        @changed="syncNavigation"
      />
      <section v-else-if="selected">
        <h1>订单</h1>
        <p>订单操作将在下一阶段接入。</p>
      </section>
      <section v-else>
        <h1>工作台</h1>
        <p>当前账号暂无可用菜单。</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.workspace {
  min-height: 100svh;
  display: grid;
  grid-template-columns: minmax(13rem, 16rem) minmax(0, 1fr);
}
.sidebar {
  position: sticky;
  top: 0;
  height: 100svh;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 2rem;
  padding: 2.1rem 1.15rem 1.35rem;
  background: var(--navy);
  color: var(--paper);
}
.brand {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding-left: 0.65rem;
  font: 600 1.3rem var(--display);
}
.brand span {
  display: grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 1px solid #b8ccbc;
  border-radius: 50%;
}
.brand span::after {
  content: '';
  background: var(--lime);
  border-radius: 50%;
  width: 0.55rem;
  height: 0.55rem;
}
.navigation {
  display: grid;
  gap: 0.25rem;
}
.navigation a {
  display: block;
  padding: 0.7rem 0.9rem;
  color: inherit;
  text-decoration: none;
  border: 1px solid transparent;
  border-radius: 0.35rem;
}
.navigation a:hover {
  border-color: #789390;
}
.navigation a[aria-current='page'] {
  color: var(--navy);
  background: var(--lime);
  font-weight: 650;
}
.identity {
  margin-top: auto;
  border-top: 1px solid #5e7774;
  padding: 1rem 0.6rem 0;
  display: grid;
  gap: 0.45rem;
  overflow-wrap: anywhere;
}
.identity button {
  margin-top: 0.5rem;
  background: transparent;
  color: var(--paper);
  border-color: #79908b;
}
.workspace-main {
  min-width: 0;
  padding: clamp(1.25rem, 4.5vw, 5rem);
}
@media (max-width: 700px) {
  .workspace {
    grid-template-columns: 1fr;
  }
  .sidebar {
    position: static;
    height: auto;
    overflow: visible;
    gap: 0.8rem;
    padding: 1rem;
  }
  .navigation {
    display: flex;
    overflow-x: auto;
  }
  .navigation a {
    white-space: nowrap;
  }
  .identity {
    grid-template-columns: auto 1fr auto;
    align-items: center;
    margin-top: 0;
    padding-top: 0.6rem;
  }
  .identity button {
    margin-top: 0;
  }
}
</style>
