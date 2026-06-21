<template>
  <div class="not-found">
    <div class="not-found-content">
      <div class="error-code">404</div>
      <h1 class="error-title">{{ t('notFound.title') }}</h1>
      <p class="error-desc">{{ t('notFound.description') }}</p>
      <button class="back-btn" @click="router.push('/')">
        {{ t('notFound.goBack') }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hlaia-bg);
  animation: fadeIn 0.4s ease;
}

.not-found-content {
  text-align: center;
  padding: 60px 40px;
}

.error-code {
  font-size: 120px;
  font-weight: 700;
  color: var(--hlaia-primary);
  opacity: 0.15;
  line-height: 1;
  margin-bottom: -20px;
  font-family: 'DM Sans', sans-serif;
}

.error-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--hlaia-text);
  margin: 0 0 12px;
  font-family: 'DM Sans', sans-serif;
}

.error-desc {
  font-size: 15px;
  color: var(--hlaia-text-muted);
  margin: 0 0 32px;
  font-family: 'DM Sans', sans-serif;
}

.back-btn {
  display: inline-block;
  padding: 10px 28px;
  background: var(--hlaia-primary);
  color: #fff;
  border: none;
  border-radius: var(--hlaia-radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.15s ease;
  font-family: 'DM Sans', sans-serif;
}

.back-btn:hover {
  background: var(--hlaia-primary-dark);
  transform: translateY(-1px);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
