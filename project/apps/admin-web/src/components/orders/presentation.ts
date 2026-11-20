import type { DetailResponse } from '../../api/generated/types.gen'

export const statuses: Record<DetailResponse['status'], string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审批',
  APPROVED: '已批准',
  CANCELLED: '已取消',
}

export const actions = {
  CREATE: '创建草稿',
  EDIT: '编辑草稿',
  SUBMIT: '提交审批',
  APPROVE: '批准订单',
  CANCEL: '取消订单',
}

const amounts = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
})

export function formatAmount(cents: number) {
  return amounts.format(cents / 100)
}

export function statusLabel(status: string) {
  return Object.hasOwn(statuses, status)
    ? statuses[status as keyof typeof statuses]
    : status
}

export function actionLabel(action: string) {
  return Object.hasOwn(actions, action)
    ? actions[action as keyof typeof actions]
    : action
}
