<script setup lang="ts">
import LoginForm from './LoginForm.vue'
import { useSession } from '../session'

const { pending, error, signIn } = useSession()
</script>

<template>
  <main class="entry">
    <aside class="cover" aria-label="订单管理">
      <span class="mark" aria-hidden="true"></span>
      <strong class="wordmark">订单管理</strong>
      <div class="ledger" aria-hidden="true"></div>
    </aside>
    <section class="sheet" aria-labelledby="login-title">
      <h1 id="login-title">登录</h1>
      <LoginForm :pending="pending" :error="error" @submit="signIn" />
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
