<script setup lang="ts">
import { shallowRef } from 'vue'
import { orders } from '../../api/generated/sdk.gen'
import type { OrdersResponse } from '../../api/generated/types.gen'
import { useSession } from '../../session'
import { usePaged } from '../../usePaged'
import PageControls from '../PageControls.vue'
import RequestNotice from '../RequestNotice.vue'
import OrderPanel from './OrderPanel.vue'
import { formatAmount, statuses } from './presentation'

const { data, profile } = useSession()
const selected = shallowRef<{ id: string | null }>()
const filter = shallowRef('')
let appliedStatus = ''
const { page, items, total, busy, failure, size, load } = usePaged<
  OrdersResponse['items'][number]
>((page, size) => {
  const query = { page, size, status: appliedStatus || undefined }
  return data((headers) => orders({ headers, query }))
})

function applyFilter() {
  appliedStatus = filter.value
  if (page.value === 1) void load()
  else page.value = 1
}

async function updateList(id: string) {
  if (selected.value?.id === null) selected.value = { id }
  await load()
  const lastPage = Math.max(1, Math.ceil(total.value / size))
  if (!busy.value && !failure.value && page.value > lastPage)
    page.value = lastPage
}
</script>

<template>
  <section class="stack" aria-labelledby="orders-title">
    <header class="page-header">
      <h1 id="orders-title">订单</h1>
      <button
        v-if="profile?.permissions.includes('orders:create')"
        type="button"
        class="primary"
        @click="selected = { id: null }"
      >
        新建订单
      </button>
    </header>
    <OrderPanel
      v-if="selected"
      :key="selected.id ?? 'new'"
      :order-id="selected.id"
      @dismiss="selected = undefined"
      @saved="updateList"
    />
    <form class="panel form-grid" @submit.prevent="applyFilter">
      <label>
        订单状态
        <select v-model="filter">
          <option value="">全部状态</option>
          <option
            v-for="(label, status) in statuses"
            :key="status"
            :value="status"
          >
            {{ label }}
          </option>
        </select>
      </label>
      <button class="secondary" type="submit" :disabled="busy">查询订单</button>
    </form>
    <RequestNotice :failure="failure" />
    <div class="panel">
      <div class="table-scroll">
        <table aria-label="订单列表">
          <thead>
            <tr>
              <th>订单号</th>
              <th>客户</th>
              <th>金额</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in items" :key="order.id">
              <td>#{{ order.id }}</td>
              <td>{{ order.customerName }}</td>
              <td>{{ formatAmount(order.totalAmountCents) }}</td>
              <td>{{ statuses[order.status] }}</td>
              <td>
                <button
                  class="secondary"
                  type="button"
                  :aria-label="`查看订单 ${order.id}`"
                  @click="selected = { id: order.id }"
                >
                  查看
                </button>
              </td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="5">
                {{ busy ? '正在读取…' : failure ? '读取失败' : '暂无对应订单' }}
              </td>
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
