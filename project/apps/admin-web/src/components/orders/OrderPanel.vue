<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import {
  approve,
  cancel,
  createOrder,
  detail,
  edit,
  history_ as history,
  submit,
} from '../../api/generated/sdk.gen'
import type {
  DetailResponse,
  HistoryResponse,
} from '../../api/generated/types.gen'
import { ApiError } from '../../api/http'
import { useSession } from '../../session'
import RequestNotice from '../RequestNotice.vue'
import {
  actionLabel,
  actions,
  formatAmount,
  statusLabel,
  statuses,
} from './presentation'

const props = defineProps<{ orderId: string | null }>()
const emit = defineEmits<{
  dismiss: []
  saved: [id: string]
  busy: [value: boolean]
}>()
const { data, profile } = useSession()
const order = shallowRef<DetailResponse>()
const records = shallowRef<HistoryResponse>([])
const customerName = shallowRef('')
const amount = shallowRef('')
const editing = shallowRef(false)
const busy = shallowRef(false)
const failure = shallowRef<unknown>()
const historyFailure = shallowRef<unknown>()
const dates = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})
const transitions = { SUBMIT: submit, APPROVE: approve, CANCEL: cancel }
const canCreate = computed(() =>
  profile.value?.permissions.includes('orders:create'),
)
const canEdit = computed(() => order.value?.allowedActions.includes('EDIT'))
const conflict = computed(
  () => failure.value instanceof ApiError && failure.value.status === 409,
)

async function readHistory(id: string) {
  historyFailure.value = undefined
  try {
    records.value = await data((headers) => history({ headers, path: { id } }))
  } catch (error) {
    records.value = []
    historyFailure.value = error
  }
}

async function perform(operation: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  emit('busy', true)
  failure.value = undefined
  try {
    await operation()
  } catch (error) {
    failure.value = error
  } finally {
    busy.value = false
    emit('busy', false)
  }
}

function reload() {
  const id = props.orderId
  if (id === null) return
  void perform(async () => {
    order.value = undefined
    editing.value = false
    records.value = []
    historyFailure.value = undefined
    order.value = await data((headers) => detail({ headers, path: { id } }))
    await readHistory(id)
  })
}

function startEdit() {
  const current = order.value
  if (!current || !canEdit.value) return
  customerName.value = current.customerName
  amount.value = (current.totalAmountCents / 100).toFixed(2)
  editing.value = true
}

function save() {
  const current = order.value
  if (props.orderId === null ? !canCreate.value : !current || !canEdit.value)
    return
  const customer = customerName.value.replace(
    /^\p{White_Space}+|\p{White_Space}+$/gu,
    '',
  )
  const [units, fraction = ''] = amount.value.split('.')
  const cents = Number(units) * 100 + Number(fraction.padEnd(2, '0'))
  void perform(async () => {
    if (
      !customer ||
      [...customer].length > 100 ||
      !/^\d{1,7}(\.\d{1,2})?$/.test(amount.value) ||
      cents < 1 ||
      cents > 999_999_999
    )
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        '请输入 1 至 100 字的客户名称，以及 0.01 至 9999999.99 元的金额。',
      )
    const body = { customerName: customer, totalAmountCents: cents }
    if (!current) {
      const created = await data((headers) => createOrder({ headers, body }))
      emit('saved', created.id)
      return
    }
    const path = { id: current.id }
    order.value = await data((headers) =>
      edit({ headers, path, body: { ...body, version: current.version } }),
    )
    editing.value = false
    emit('saved', current.id)
    await readHistory(current.id)
  })
}

function transition(action: keyof typeof transitions) {
  const current = order.value
  if (!current?.allowedActions.includes(action)) return
  const path = { id: current.id }
  const body = { version: current.version }
  void perform(async () => {
    order.value = await data((headers) =>
      transitions[action]({ headers, path, body }),
    )
    emit('saved', current.id)
    await readHistory(current.id)
  })
}

reload()
</script>

<template>
  <section
    class="panel stack"
    aria-labelledby="order-panel-title"
    :aria-busy="busy"
  >
    <header class="actions">
      <h2 id="order-panel-title">
        {{ orderId === null ? '新建订单' : `订单 #${orderId}` }}
      </h2>
      <button
        type="button"
        class="secondary"
        :disabled="busy"
        @click="emit('dismiss')"
      >
        关闭
      </button>
    </header>
    <RequestNotice :failure="failure" />
    <div
      v-if="orderId !== null && (conflict || (!order && !busy))"
      class="actions"
    >
      <button type="button" class="secondary" :disabled="busy" @click="reload">
        重新读取订单
      </button>
    </div>
    <form
      v-if="(orderId === null && canCreate) || (editing && canEdit)"
      class="stack"
      aria-label="订单草稿"
      @submit.prevent="save"
    >
      <div class="form-grid">
        <label
          >客户名称
          <input
            v-model="customerName"
            name="customer"
            :disabled="busy"
            required
        /></label>
        <label
          >金额（元）
          <input
            v-model="amount"
            name="amount"
            type="text"
            inputmode="decimal"
            :disabled="busy"
            placeholder="0.00"
            required
        /></label>
      </div>
      <div class="actions">
        <button type="submit" class="primary" :disabled="busy || conflict">
          {{ orderId === null ? '创建草稿' : '保存草稿' }}
        </button>
        <button
          v-if="editing"
          type="button"
          class="secondary"
          :disabled="busy"
          @click="editing = false"
        >
          返回详情
        </button>
      </div>
    </form>
    <template v-else-if="order">
      <dl class="order-facts">
        <div>
          <dt>客户</dt>
          <dd>{{ order.customerName }}</dd>
        </div>
        <div>
          <dt>金额</dt>
          <dd>{{ formatAmount(order.totalAmountCents) }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ statuses[order.status] }}</dd>
        </div>
        <div>
          <dt>版本</dt>
          <dd>{{ order.version }}</dd>
        </div>
      </dl>
      <div v-if="order.allowedActions.length" class="actions">
        <button
          v-if="canEdit"
          type="button"
          class="secondary"
          :disabled="busy || conflict"
          @click="startEdit"
        >
          {{ actions.EDIT }}
        </button>
        <template v-for="action in order.allowedActions" :key="action">
          <button
            v-if="action !== 'EDIT'"
            type="button"
            :class="action === 'CANCEL' ? 'danger' : 'primary'"
            :disabled="busy || conflict"
            @click="transition(action)"
          >
            {{ actions[action] }}
          </button>
        </template>
      </div>
    </template>
    <p v-else-if="busy && orderId !== null" role="status">正在读取订单…</p>
    <div v-if="order" class="stack">
      <h3>操作历史</h3>
      <RequestNotice :failure="historyFailure" />
      <button
        v-if="historyFailure"
        type="button"
        class="secondary"
        :disabled="busy"
        @click="perform(() => readHistory(order!.id))"
      >
        重试历史查询
      </button>
      <div v-else class="table-scroll">
        <table aria-label="操作历史">
          <thead>
            <tr>
              <th>时间</th>
              <th>动作</th>
              <th>状态变更</th>
              <th>操作者</th>
              <th>版本</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="event in records" :key="event.version">
              <td>{{ dates.format(new Date(event.occurredAt)) }}</td>
              <td>{{ actionLabel(event.action) }}</td>
              <td>
                {{
                  event.fromStatus ? `${statusLabel(event.fromStatus)} → ` : ''
                }}{{ statusLabel(event.toStatus) }}
              </td>
              <td>#{{ event.actorId }}</td>
              <td>{{ event.version }}</td>
            </tr>
            <tr v-if="!records.length">
              <td colspan="5">{{ busy ? '正在读取…' : '暂无操作记录' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<style scoped>
header {
  justify-content: space-between;
}
.panel h2,
h3 {
  margin: 0;
}
.order-facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(10rem, 100%), 1fr));
  gap: 1rem;
  margin: 0;
  padding-block: 0.85rem;
  border-block: 1px solid var(--line);
}
dt {
  font-size: 0.84rem;
  color: #48615b;
}
dd {
  margin: 0.35rem 0 0;
  font-weight: 650;
  overflow-wrap: anywhere;
}
</style>
