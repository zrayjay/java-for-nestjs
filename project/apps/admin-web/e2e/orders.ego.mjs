import assert from 'node:assert/strict'

export async function verifyOrders(page, fixture) {
  const origin = new URL(fixture.origin)
  assert.ok(['127.0.0.1', 'localhost'].includes(origin.hostname))
  await page.goto(new URL('/#orders', origin).href)
  await page.reload()

  const users = await page.evaluate(
    async ({ admin, password, outsideDepartment }) => {
      async function api(route, method, body, token) {
        const response = await fetch('/api/v1' + route, {
          method,
          headers: {
            ...(body && { 'Content-Type': 'application/json' }),
            ...(token && { Authorization: 'Bearer ' + token }),
          },
          body: body ? JSON.stringify(body) : undefined,
        })
        if (!response.ok)
          throw new Error(method + ' ' + route + ' ' + response.status)
        return response.status === 204 ? undefined : response.json()
      }
      const { accessToken } = await api('/auth/login', 'POST', admin)
      const departments = await api(
        '/departments',
        'GET',
        undefined,
        accessToken,
      )
      const local = departments.find(
        (department) => department.code === 'operations',
      )
      const outside = departments.find(
        (department) => department.code === outsideDepartment,
      )
      if (!local || !outside || local.id === outside.id)
        throw new Error('Two distinct fixture departments are required')
      const { items: roles } = await api(
        '/roles?size=100',
        'GET',
        undefined,
        accessToken,
      )
      const suffix = Date.now().toString(36)
      async function create(kind, role, departmentId) {
        const username = 'e2e-' + kind + '-' + suffix
        const user = await api(
          '/users',
          'POST',
          { username, password, displayName: username, departmentId },
          accessToken,
        )
        const roleId = roles.find(({ code }) => code === role)?.id
        if (!roleId) throw new Error('Missing fixture role ' + role)
        await api(
          '/users/' + user.id + '/roles',
          'PUT',
          { roleIds: [roleId] },
          accessToken,
        )
        return { username, password }
      }
      return {
        clerk: await create('clerk', 'ORDER_CLERK', local.id),
        reviewer: await create('reviewer', 'ORDER_REVIEWER', local.id),
        outside: await create('outside', 'ORDER_REVIEWER', outside.id),
        customer: '浏览器订单 ' + suffix,
      }
    },
    fixture,
  )

  const css = (selector) => 'loc=css:' + selector
  const visible = (selector) =>
    page.waitForSelector(css(selector), { state: 'visible' })
  const click = (selector, label) => page.click(css(selector), { label })
  const button = (name, label) =>
    page.click('loc=role:button[name="' + name + '"]', { label })
  const ready = () =>
    page.waitForFunction(
      () =>
        document
          .querySelector('#order-panel-title')
          ?.closest('section')
          ?.getAttribute('aria-busy') === 'false',
    )
  const status = (label) =>
    page.waitForFunction(
      (label) =>
        [...document.querySelectorAll('.order-facts dd')].some(
          (cell) => cell.textContent === label,
        ),
      label,
    )
  async function login({ username, password }) {
    await visible('#login-title')
    await page.fill(css('input[name="username"]'), username)
    await page.fill(css('input[name="password"]'), password)
    await click('button[type="submit"]', 'Sign in fixture account')
    await visible('#orders-title')
  }
  const logout = () => click('.identity button', 'Switch fixture accounts')
  const show = (id) =>
    click('button[aria-label="查看订单 ' + id + '"]', 'Open the matching order')
  async function draft(customer, amount, label) {
    await visible('input[name="customer"]')
    await page.fill(css('input[name="customer"]'), customer)
    await page.fill(css('input[name="amount"]'), amount)
    await click('form[aria-label="订单草稿"] button[type="submit"]', label)
  }
  async function direct(credentials, route, method = 'GET', body) {
    return page.evaluate(
      async ({ credentials, route, method, body }) => {
        const session = await fetch('/api/v1/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(credentials),
        })
        if (!session.ok)
          throw new Error('Fixture login failed ' + session.status)
        const { accessToken } = await session.json()
        const result = await fetch('/api/v1' + route, {
          method,
          headers: {
            Authorization: 'Bearer ' + accessToken,
            ...(body && { 'Content-Type': 'application/json' }),
          },
          body: body ? JSON.stringify(body) : undefined,
        })
        return result.status
      },
      { credentials, route, method, body },
    )
  }

  await login(users.clerk)
  await button('新建订单', 'Open the order form')
  await draft(users.customer, '153.29', 'Create the order draft')
  await status('草稿')
  await ready()
  const id = await page.evaluate(
    () =>
      document.querySelector('#order-panel-title').textContent.split('#')[1],
  )
  await button('编辑草稿', 'Open the draft editor')
  await draft(users.customer + ' 更新', '181.05', 'Save the edited draft')
  await ready()

  // A second session edits between the first session's read and write.
  assert.equal(
    await direct(users.clerk, '/orders/' + id, 'PATCH', {
      customerName: users.customer + ' 并发更新',
      totalAmountCents: 18105,
      version: 2,
    }),
    200,
  )
  await button('提交审批', 'Submit a stale version')
  await page.waitForFunction(
    () =>
      document
        .querySelector('[role="alert"]')
        ?.textContent.includes('记录已经变化') &&
      document
        .querySelector('[role="alert"] small')
        ?.textContent.includes('请求 ID'),
  )
  await button('重新读取订单', 'Read the current version')
  await status('草稿')
  await ready()
  await button('提交审批', 'Submit the current draft')
  await status('待审批')
  await ready()
  assert.equal(
    await direct(users.clerk, '/orders/' + id + '/approve', 'POST', {
      version: 4,
    }),
    403,
  )
  await logout()

  await login(users.outside)
  await page.waitForFunction(() =>
    document
      .querySelector('table[aria-label="订单列表"] tbody')
      ?.textContent.includes('暂无对应订单'),
  )
  assert.equal(await direct(users.outside, '/orders/' + id), 404)
  assert.equal(await direct(users.outside, '/users'), 403)
  await logout()

  await login(users.reviewer)
  await visible('button[aria-label="查看订单 ' + id + '"]')
  await show(id)
  await status('待审批')
  await ready()
  const capabilities = await page.evaluate(() =>
    [...document.querySelectorAll('.order-facts + .actions button')].map(
      (button) => button.textContent.trim(),
    ),
  )
  assert.deepEqual([...capabilities].sort(), ['取消订单', '批准订单'].sort())
  await button('批准订单', 'Approve as department reviewer')
  await status('已批准')
  await page.waitForFunction(() =>
    document
      .querySelector('table[aria-label="操作历史"] tbody')
      ?.textContent.includes('批准订单'),
  )
  assert.equal(
    await page.evaluate(
      () =>
        document.querySelectorAll('table[aria-label="操作历史"] tbody tr')
          .length,
    ),
    5,
  )
  await page.selectOption(css('select'), { value: 'APPROVED' })
  await button('查询订单', 'Filter to approved orders')
  await visible('button[aria-label="查看订单 ' + id + '"]')
  await logout()

  await login(users.clerk)
  await button('新建订单', 'Create a second order')
  await draft(
    users.customer + ' 取消演练',
    '0.01',
    'Save draft for cancellation',
  )
  await status('草稿')
  await ready()
  await button('取消订单', 'Cancel the second draft')
  await status('已取消')
  await page.waitForFunction(() =>
    document
      .querySelector('table[aria-label="操作历史"] tbody')
      ?.textContent.includes('取消订单'),
  )
  return {
    approvedOrderId: id,
    historyEntries: 5,
    conflict: 409,
    outsideDepartment: 404,
    forbidden: 403,
    cancelled: true,
  }
}
