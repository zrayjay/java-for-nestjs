<script setup lang="ts">
import { reactive } from 'vue'
import { page as readAudit } from '../../api/generated/sdk.gen'
import { useSession } from '../../session'
import { usePaged } from '../../usePaged'
import PageControls from '../PageControls.vue'
import RequestNotice from '../RequestNotice.vue'

const { data } = useSession()
const filters = reactive({ type: '', id: '' })
let filter = { type: '', id: '' }
const { page, items, total, busy, failure, size, load } = usePaged(
  (page, size) =>
    data((headers) =>
      readAudit({
        headers,
        query: {
          page,
          size,
          resourceType: filter.type || undefined,
          resourceId: filter.id || undefined,
        },
      }),
    ),
)
const dates = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function applyFilters() {
  filter = { type: filters.type, id: filters.id.trim() }
  if (page.value === 1) void load()
  else page.value = 1
}
</script>

<template>
  <section class="stack" aria-labelledby="audit-title">
    <header class="page-header"><h1 id="audit-title">审计</h1></header>
    <form class="panel form-grid" @submit.prevent="applyFilters">
      <label
        >资源类型
        <select v-model="filters.type">
          <option value="">全部</option>
          <option value="ORDER">订单</option>
          <option value="USER">用户</option>
          <option value="ROLE">角色</option>
          <option value="MENU">菜单</option>
        </select>
      </label>
      <label
        >资源 ID
        <input
          v-model="filters.id"
          name="resource-id"
          inputmode="numeric"
          pattern="[0-9]*"
      /></label>
      <button class="secondary" type="submit" :disabled="busy">查询</button>
    </form>
    <RequestNotice :failure="failure" />
    <div class="panel">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>动作</th>
              <th>资源</th>
              <th>操作者</th>
              <th>请求 ID</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="event in items" :key="event.id">
              <td>{{ dates.format(new Date(event.occurredAt)) }}</td>
              <td>{{ event.action }}</td>
              <td>{{ event.resourceType }} #{{ event.resourceId }}</td>
              <td>{{ event.actorId }}</td>
              <td>
                <code>{{ event.requestId }}</code>
              </td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="5">{{ busy ? '正在读取…' : '暂无对应审计记录' }}</td>
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
