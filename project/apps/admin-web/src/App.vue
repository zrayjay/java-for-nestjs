<script setup lang="ts">
import { shallowRef } from 'vue'
import LoginForm from './components/LoginForm.vue'
import { login, logout, me } from './api/generated/sdk.gen'
import type { LoginResponse, MeResponse } from './api/generated/types.gen'
import { ApiError, bearer, expectData, request } from './api/http'

const pending = shallowRef(false)
const profile = shallowRef<MeResponse | null>(null)
const error = shallowRef<ApiError | null>(null)
let session: LoginResponse | undefined

async function signIn(credentials: { username: string; password: string }) {
  if (pending.value) return
  pending.value = true
  error.value = null
  try {
    const next = await expectData(() => login({ body: credentials }))
    const { accessToken, refreshToken } = next
    if (!accessToken || !refreshToken) {
      throw new ApiError(0, 'EMPTY_RESPONSE', '服务未返回完整的会话。')
    }
    const account = await expectData(() => me({ headers: bearer(accessToken) }))
    session = next
    profile.value = account
  } catch (failure) {
    if (failure instanceof ApiError) error.value = failure
  } finally {
    pending.value = false
  }
}

async function signOut() {
  const current = session
  session = undefined
  profile.value = null
  if (current?.accessToken && current.refreshToken) {
    await request(() =>
      logout({
        headers: bearer(current.accessToken!),
        body: { refreshToken: current.refreshToken },
      }),
    ).catch((failure) => {
      if (failure instanceof ApiError && failure.status !== 401)
        error.value = failure
    })
  }
}
</script>

<template>
  <main class="entry">
    <aside class="cover" aria-label="订单管理">
      <span class="mark" aria-hidden="true"></span>
      <strong class="wordmark">订单管理</strong>
      <div class="ledger" aria-hidden="true"></div>
    </aside>
    <section
      class="sheet"
      :aria-labelledby="profile ? 'profile-title' : 'login-title'"
    >
      <template v-if="profile">
        <h1 id="profile-title">
          {{ profile.displayName || profile.username }}
        </h1>
        <dl>
          <dt>用户名</dt>
          <dd>{{ profile.username }}</dd>
          <dt>部门</dt>
          <dd>{{ profile.departmentId }}</dd>
        </dl>
        <button type="button" class="secondary" @click="signOut">
          退出登录
        </button>
      </template>
      <template v-else>
        <h1 id="login-title">登录</h1>
        <LoginForm :pending="pending" :error="error" @submit="signIn" />
      </template>
    </section>
  </main>
</template>

<style scoped>
.entry {
  min-height: 100svh;
  display: grid;
  grid-template-columns: minmax(21rem, 0.8fr) minmax(25rem, 1fr);
}

.cover {
  display: grid;
  align-content: space-between;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  background: var(--navy);
  color: var(--paper);
  padding: clamp(2rem, 6vw, 6rem);
}

.mark {
  width: 2.4rem;
  height: 2.4rem;
  border: 1px solid #a9c4bd;
  border-radius: 50%;
  background: radial-gradient(
    circle at center,
    var(--lime) 0 23%,
    transparent 25%
  );
}

.wordmark {
  position: relative;
  font-family: var(--display);
  font-size: clamp(2.3rem, 6vw, 5.3rem);
  font-weight: 600;
  letter-spacing: 0.05em;
}

.ledger {
  z-index: -1;
  position: absolute;
  inset: 20% -31% 15% 27%;
  transform: rotate(-19deg);
  border: 1px solid rgba(218, 232, 181, 0.25);
  background: repeating-linear-gradient(
    transparent 0 23px,
    rgba(218, 232, 181, 0.22) 24px 25px
  );
}

.sheet {
  place-self: center stretch;
  padding: clamp(2rem, 7vw, 8rem);
}

.sheet h1 {
  margin: 0 0 2.25rem;
}

.sheet dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.75rem 1.75rem;
  margin-bottom: 2rem;
}

.sheet dd {
  margin: 0;
}

@media (max-width: 760px) {
  .entry {
    min-height: 100svh;
    grid-template-columns: 1fr;
    grid-template-rows: 10rem auto;
  }

  .cover {
    padding: 1.75rem 2rem;
  }

  .mark {
    width: 1.5rem;
    height: 1.5rem;
  }

  .sheet {
    place-self: start stretch;
  }
}
</style>
