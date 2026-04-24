<!--
  LoginView.vue — 登录页面

  设计风格：温暖极简浅色主题
  - 背景：暖白色 #FAFAF8
  - 卡片：纯白 #FFFFFF + 柔和阴影
  - 主色调：蓝色系 #4A7FC7
  - 字体：DM Sans（几何无衬线，现代感强）

  使用的 Element Plus 组件：
  - el-form / el-form-item：表单布局 + 校验规则
  - el-input：输入框
  - el-button：按钮（支持 loading 状态）

  国际化：使用 vue-i18n 的 useI18n() 组合式 API
-->
<template>
  <div class="login-page">
    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- Logo 区域 -->
      <div class="card-header">
        <div class="logo-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <rect x="2" y="2" width="28" height="28" rx="6" stroke="currentColor" stroke-width="2" />
            <path d="M10 12h12M10 16h8M10 20h10" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </div>
        <h1 class="app-title">HLAIA</h1>
        <p class="app-subtitle">{{ t('auth.login.subtitle') }}</p>
      </div>

      <!-- 表单区域 -->
      <!-- :model 绑定表单数据，:rules 绑定校验规则，ref 用于调用 validate 方法 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            :placeholder="t('auth.login.username')"
            size="large"
            :prefix-icon="UserIcon"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('auth.login.password')"
            size="large"
            :prefix-icon="LockIcon"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="authStore.loading"
            @click="handleLogin"
          >
            {{ t('auth.login.signIn') }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 底部链接 -->
      <div class="card-footer">
        <span class="footer-text">{{ t('auth.login.noAccount') }}</span>
        <router-link to="/register" class="footer-link">{{ t('auth.login.createOne') }}</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * <script setup> 是 Vue 3 的编译期语法糖
 * - 顶层变量/函数自动暴露给 template，不需要 return
 * - 导入的组件可以直接在 template 中使用，不需要 components 选项
 * - 比 Options API 更简洁，是 Vue 3 推荐的写法
 */
import { ref, reactive, shallowRef, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

// vue-i18n 组合式 API：t() 函数用于在 JS 中获取国际化文本
const { t } = useI18n()

// Element Plus 图标需要用 shallowRef 包装避免响应式代理的性能开销
const UserIcon = shallowRef({ render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [h('path', { d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }), h('circle', { cx: '12', cy: '7', r: '4' })]) })
const LockIcon = shallowRef({ render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [h('rect', { x: '3', y: '11', width: '18', height: '11', rx: '2' }), h('path', { d: 'M7 11V7a5 5 0 0 1 10 0v4' })]) })

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// reactive() 创建响应式对象，适合表单这种有多个字段的数据
const form = reactive({
  username: '',
  password: ''
})

// ref() 获取模板引用，这里用来拿到 el-form 组件实例
const formRef = ref(null)

// Element Plus 表单校验规则
// required: 必填；min/max: 最小/最大长度；trigger: 触发校验的时机
// 使用 t() 函数让校验提示信息也支持国际化
const rules = {
  username: [
    { required: true, message: () => t('auth.login.validation.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 50, message: () => t('auth.login.validation.usernameLength'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: () => t('auth.login.validation.passwordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('auth.login.validation.passwordLength'), trigger: 'blur' }
  ]
}

async function handleLogin() {
  // 先执行表单校验，valid 为 true 才继续
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    await authStore.loginAction({
      username: form.username,
      password: form.password
    })

    ElMessage.success(t('auth.login.toast.success'))

    // 登录成功后跳转：
    // 1. 如果 URL 中有 redirect 参数（说明是被拦截后跳到登录的），回到原来想去的页面
    // 2. 否则跳转到首页
    const redirect = route.query.redirect || '/'
    router.replace(redirect)
  } catch {
    // 错误已经由 axios 拦截器统一处理了，这里不需要额外处理
  }
}
</script>

<style scoped>
/* scoped 表示样式只作用于当前组件，Vue 通过给 DOM 添加 data-v-xxx 属性实现 */

/* ---- 页面容器 ---- */
/* 使用温暖极简浅色主题的 CSS 变量，变量定义在 App.vue 的 :root 中 */
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hlaia-bg);
  position: relative;
}

/* ---- 登录卡片 ---- */
/* 白色卡片 + 柔和阴影，替代之前的深色玻璃拟态风格 */
.login-card {
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

.login-card:hover {
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

/* ---- 表单样式覆盖 ---- */
/* Element Plus 的组件使用 CSS 变量，我们通过 :deep() 穿透 scoped 来自定义样式 */
.login-form {
  margin-top: 8px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.el-input__wrapper) {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius);
  box-shadow: none;
  padding: 4px 12px;
  transition: all 0.3s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: var(--hlaia-primary-light);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.15);
}

.login-form :deep(.el-input__inner) {
  color: var(--hlaia-text);
  font-family: 'DM Sans', sans-serif;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: var(--hlaia-text-muted);
}

.login-form :deep(.el-input__prefix .el-icon) {
  color: var(--hlaia-text-muted);
}

/* 校验错误时的输入框样式 */
.login-form :deep(.el-form-item.is-error .el-input__wrapper) {
  border-color: #e74c3c;
  box-shadow: 0 0 0 2px rgba(231, 76, 60, 0.12);
}

.login-form :deep(.el-form-item__error) {
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
