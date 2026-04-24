<!--
  RegisterView.vue — 注册页面

  与登录页面共享相同的视觉风格（温暖极简浅色主题）。
  额外增加了"确认密码"字段和自定义校验器（检查两次密码是否一致）。

  国际化：使用 vue-i18n 的 useI18n() 组合式 API
-->
<template>
  <div class="register-page">
    <!-- 注册卡片 -->
    <div class="register-card">
      <!-- 头部 -->
      <div class="card-header">
        <div class="logo-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <rect x="2" y="2" width="28" height="28" rx="6" stroke="currentColor" stroke-width="2" />
            <path d="M10 12h12M10 16h8M10 20h10" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </div>
        <h1 class="app-title">HLAIA</h1>
        <p class="app-subtitle">{{ t('auth.register.subtitle') }}</p>
      </div>

      <!-- 表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="register-form"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            :placeholder="t('auth.register.username')"
            size="large"
            :prefix-icon="UserIcon"
          />
        </el-form-item>

        <!-- 昵称字段（可选）：位于用户名和密码之间，方便用户在注册时自定义显示名称 -->
        <el-form-item prop="nickname">
          <el-input
            v-model="form.nickname"
            :placeholder="t('auth.register.nicknamePlaceholder')"
            size="large"
            :prefix-icon="NicknameIcon"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('auth.register.password')"
            size="large"
            :prefix-icon="LockIcon"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="t('auth.register.confirmPassword')"
            size="large"
            :prefix-icon="LockIcon"
            show-password
            @keyup.enter="handleRegister"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="authStore.loading"
            @click="handleRegister"
          >
            {{ t('auth.register.createAccount') }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 底部链接 -->
      <div class="card-footer">
        <span class="footer-text">{{ t('auth.register.hasAccount') }}</span>
        <router-link to="/login" class="footer-link">{{ t('auth.register.signIn') }}</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, shallowRef, h } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

// vue-i18n 组合式 API：t() 函数用于在 JS 中获取国际化文本
const { t } = useI18n()

// 自定义 SVG 图标组件（不依赖 @element-plus/icons-vue 额外安装）
const UserIcon = shallowRef({ render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [h('path', { d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }), h('circle', { cx: '12', cy: '7', r: '4' })]) })
const LockIcon = shallowRef({ render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [h('rect', { x: '3', y: '11', width: '18', height: '11', rx: '2' }), h('path', { d: 'M7 11V7a5 5 0 0 1 10 0v4' })]) })
// 昵称图标：笑脸造型，与用户名图标（人形）做区分，暗示"给自己取个有趣的名字"
const NicknameIcon = shallowRef({ render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [h('circle', { cx: '12', cy: '12', r: '10' }), h('path', { d: 'M8 14s1.5 2 4 2 4-2 4-2' }), h('circle', { cx: '9', cy: '9', r: '0.5', fill: 'currentColor', stroke: 'none' }), h('circle', { cx: '15', cy: '9', r: '0.5', fill: 'currentColor', stroke: 'none' })]) })

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const formRef = ref(null)

/**
 * 自定义校验器：确认密码
 *
 * Element Plus 的 validator 接收 (rule, value, callback) 三个参数：
 * - rule: 当前校验规则的配置
 * - value: 被校验字段的当前值
 * - callback: 调用 callback() 表示通过，callback(new Error('msg')) 表示不通过
 */
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error(t('auth.register.validation.passwordMismatch')))
  } else {
    callback()
  }
}

// 使用 t() 函数的函数形式，确保校验消息在语言切换时也能更新
const rules = {
  username: [
    { required: true, message: () => t('auth.register.validation.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 50, message: () => t('auth.register.validation.usernameLength'), trigger: 'blur' }
  ],
  // 昵称是可选字段（没有 required），只校验最大长度
  nickname: [
    { max: 15, message: () => t('auth.register.validation.nicknameLength'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: () => t('auth.register.validation.passwordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('auth.register.validation.passwordLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: () => t('auth.register.validation.confirmPasswordRequired'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    await authStore.registerAction({
      username: form.username,
      nickname: form.nickname,
      password: form.password
    })

    ElMessage.success(t('auth.register.toast.success'))
    // 注册成功后直接跳转到首页（后端已返回 Token，Store 已处理登录状态）
    router.replace('/')
  } catch {
    // 错误已由 axios 拦截器处理
  }
}
</script>

<style scoped>
/* 样式与 LoginView 保持一致，确保视觉统一 */

/* ---- 页面容器 ---- */
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hlaia-bg);
  position: relative;
}

/* ---- 注册卡片 ---- */
.register-card {
  position: relative;
  z-index: 1;
  width: 400px;
  padding: 48px 40px 40px;
  border-radius: var(--hlaia-radius-lg);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow);
  transition: box-shadow 0.3s ease;
}

.register-card:hover {
  box-shadow: var(--hlaia-shadow-hover);
}

/* ---- 头部 ---- */
.card-header {
  text-align: center;
  margin-bottom: 36px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: var(--hlaia-primary);
  color: #fff;
  margin-bottom: 16px;
  box-shadow: 0 4px 16px rgba(74, 127, 199, 0.25);
}

.app-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--hlaia-text);
  margin: 0 0 6px;
}

.app-subtitle {
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  color: var(--hlaia-text-muted);
  margin: 0;
}

/* ---- 表单样式 ---- */
.register-form {
  margin-top: 8px;
}

.register-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.register-form :deep(.el-input__wrapper) {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius);
  box-shadow: none;
  padding: 4px 12px;
  transition: all 0.3s ease;
}

.register-form :deep(.el-input__wrapper:hover) {
  border-color: var(--hlaia-primary-light);
}

.register-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.15);
}

.register-form :deep(.el-input__inner) {
  color: var(--hlaia-text);
  font-family: 'DM Sans', sans-serif;
}

.register-form :deep(.el-input__inner::placeholder) {
  color: var(--hlaia-text-muted);
}

.register-form :deep(.el-input__prefix .el-icon) {
  color: var(--hlaia-text-muted);
}

.register-form :deep(.el-form-item.is-error .el-input__wrapper) {
  border-color: #e74c3c;
  box-shadow: 0 0 0 2px rgba(231, 76, 60, 0.12);
}

.register-form :deep(.el-form-item__error) {
  font-family: 'DM Sans', sans-serif;
  color: #e74c3c;
  padding-top: 4px;
}

/* ---- 提交按钮 ---- */
.submit-btn {
  width: 100%;
  height: 46px;
  border-radius: var(--hlaia-radius);
  font-family: 'DM Sans', sans-serif;
  font-weight: 600;
  font-size: 15px;
  letter-spacing: 0.5px;
  background: var(--hlaia-primary) !important;
  border: none;
  color: #fff !important;
  transition: all 0.3s ease;
}

.submit-btn:hover {
  background: var(--hlaia-primary-light) !important;
  box-shadow: 0 4px 16px rgba(74, 127, 199, 0.3);
  transform: translateY(-1px);
}

.submit-btn:active {
  transform: translateY(0);
}

/* ---- 底部 ---- */
.card-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--hlaia-border);
}

.footer-text {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}

.footer-link {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-primary);
  text-decoration: none;
  margin-left: 4px;
  transition: color 0.2s ease;
}

.footer-link:hover {
  color: var(--hlaia-primary-dark);
}
</style>
