<!--
  SettingsView.vue — 个人设置页面

  设计风格：
  - Warm Minimal Light 主题，与 StagingView 保持一致
  - 顶部导航栏复用 NavBar 共享组件
  - 两个卡片：基本信息编辑 + 修改密码
  - 最大宽度 640px 居中布局，干净留白

  功能：
  - 页面加载时从后端拉取用户信息填充表单
  - 编辑昵称/邮箱后保存
  - 修改密码（含前后端校验）
-->
<template>
  <div class="settings-layout">
    <!-- 顶部导航栏（共享组件） -->
    <NavBar />

    <!-- 主内容区 -->
    <main class="settings-content">
      <!-- 页面标题 -->
      <h1 class="settings-page-title">{{ t('settings.title') }}</h1>

      <!-- 卡片1：基本信息 -->
      <div class="settings-card">
        <h2 class="card-title">{{ t('settings.profile.title') }}</h2>

        <!-- 用户名（只读展示，不可编辑） -->
        <div class="info-row">
          <label class="info-label">Username</label>
          <span class="info-value">{{ profile.username }}</span>
        </div>

        <!-- 可编辑的昵称和邮箱表单 -->
        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-position="top"
          class="settings-form"
        >
          <el-form-item :label="t('settings.profile.nickname')" prop="nickname">
            <el-input
              v-model="profileForm.nickname"
              :placeholder="t('settings.profile.nicknamePlaceholder')"
              maxlength="50"
              clearable
            />
          </el-form-item>

          <el-form-item :label="t('settings.profile.email')" prop="email">
            <el-input
              v-model="profileForm.email"
              :placeholder="t('settings.profile.emailPlaceholder')"
              maxlength="100"
              clearable
            />
          </el-form-item>

          <el-form-item>
            <button
              type="button"
              class="action-btn"
              :disabled="profileSaving"
              @click="handleSaveProfile"
            >
              <span v-if="profileSaving" class="btn-spinner"></span>
              {{ t('settings.profile.save') }}
            </button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 卡片2：修改密码 -->
      <div class="settings-card">
        <h2 class="card-title">{{ t('settings.password.title') }}</h2>

        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-position="top"
          class="settings-form"
        >
          <el-form-item :label="t('settings.password.oldPassword')" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              :placeholder="t('settings.password.oldPasswordPlaceholder')"
              show-password
            />
          </el-form-item>

          <el-form-item :label="t('settings.password.newPassword')" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              :placeholder="t('settings.password.newPasswordPlaceholder')"
              show-password
            />
          </el-form-item>

          <el-form-item :label="t('settings.password.confirmPassword')" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              :placeholder="t('settings.password.confirmPasswordPlaceholder')"
              show-password
              @keyup.enter="handleChangePassword"
            />
          </el-form-item>

          <el-form-item>
            <button
              type="button"
              class="action-btn"
              :disabled="passwordSaving"
              @click="handleChangePassword"
            >
              <span v-if="passwordSaving" class="btn-spinner"></span>
              {{ t('settings.password.change') }}
            </button>
          </el-form-item>
        </el-form>
      </div>
    </main>
  </div>
</template>

<script setup>
/**
 * SettingsView — 个人设置页面
 *
 * 职责：
 * - 展示和编辑用户基本信息（昵称、邮箱）
 * - 修改登录密码（需验证旧密码）
 * - 用户名只读展示，不可修改
 *
 * 两个独立的 el-form：
 * - profileForm：昵称 + 邮箱，调用 PUT /api/user/profile
 * - passwordForm：旧密码 + 新密码 + 确认密码，调用 PUT /api/user/password
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import NavBar from '@/components/NavBar.vue'
import { getProfile, updateProfile, changePassword } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()

// ---- 用户原始数据（从后端获取） ----
const profile = ref({
  username: '',
  nickname: '',
  email: ''
})

// ---- 基本信息表单 ----
const profileFormRef = ref(null)
const profileSaving = ref(false)

/**
 * reactive() 创建响应式表单对象
 * 编辑时修改这个对象，不影响原始 profile 数据
 */
const profileForm = reactive({
  nickname: '',
  email: ''
})

/**
 * 基本信息表单校验规则
 * - email 使用自定义校验器，检查格式是否合法
 */
const profileRules = {
  email: [
    {
      validator: (rule, value, callback) => {
        // 邮箱为空时允许通过（非必填字段）
        if (!value) {
          callback()
          return
        }
        // 简单的邮箱格式正则校验
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!emailRegex.test(value)) {
          callback(new Error('Please enter a valid email address'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// ---- 修改密码表单 ----
const passwordFormRef = ref(null)
const passwordSaving = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

/**
 * 自定义校验器：确认密码必须与新密码一致
 * 这是 Vue + Element Plus 中实现"两次输入一致性校验"的常用模式
 */
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error(t('settings.password.toast.mismatch')))
  } else {
    callback()
  }
}

/**
 * 密码表单校验规则
 * - trigger: 'blur' 表示输入框失焦时触发校验
 * - min: 6 要求新密码至少 6 个字符
 */
const passwordRules = {
  oldPassword: [
    { required: true, message: () => t('settings.password.validation.oldRequired'), trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: () => t('settings.password.validation.newRequired'), trigger: 'blur' },
    { min: 6, message: () => t('settings.password.validation.newLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: () => t('settings.password.validation.confirmRequired'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// ---- 生命周期：页面加载时获取用户信息 ----

onMounted(async () => {
  try {
    // getProfile() 返回 { code, data: { ... } }，响应拦截器已经解包了 code 层
    const res = await getProfile()
    const data = res.data
    profile.value = {
      username: data.username,
      nickname: data.nickname || '',
      email: data.email || ''
    }
    // 将原始数据同步到编辑表单
    profileForm.nickname = profile.value.nickname
    profileForm.email = profile.value.email
  } catch {
    ElMessage.error(t('settings.profile.toast.loadFailed'))
  }
})

// ---- 保存基本信息 ----

async function handleSaveProfile() {
  // formRef.value.validate() 返回 Promise，校验失败会 reject
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return

  profileSaving.value = true
  try {
    await updateProfile({
      nickname: profileForm.nickname.trim(),
      email: profileForm.email.trim()
    })
    // 同步更新本地 profile 展示数据
    profile.value.nickname = profileForm.nickname.trim()
    authStore.nickname = profileForm.nickname.trim()
    profile.value.email = profileForm.email.trim()
    ElMessage.success(t('settings.profile.toast.saved'))
  } catch {
    ElMessage.error(t('settings.profile.toast.saveFailed'))
  } finally {
    profileSaving.value = false
  }
}

// ---- 修改密码 ----

async function handleChangePassword() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  passwordSaving.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success(t('settings.password.toast.changed'))
    // 修改成功后清空密码表单
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    // 清除校验状态，避免残留的错误提示
    passwordFormRef.value.resetFields()
  } catch {
    // 如果是旧密码错误，后端会返回 400/401，提示用户
    ElMessage.error(t('settings.password.toast.wrongOld'))
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
/* ---- 页面布局 ---- */
/* 与 StagingView 保持一致的页面结构 */
.settings-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--hlaia-bg);
}

/* ---- 主内容区 ---- */
.settings-content {
  position: relative;
  z-index: 1;
  flex: 1;
  max-width: 640px;
  width: 100%;
  margin: 0 auto;
  padding: 32px 24px 48px;
}

/* ---- 页面标题 ---- */
.settings-page-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--hlaia-text);
  margin: 0 0 28px;
}

/* ---- 卡片容器 ---- */
.settings-card {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
  box-shadow: var(--hlaia-shadow);
  padding: 28px;
  margin-bottom: 24px;
  transition: box-shadow 0.3s ease;
}

.settings-card:hover {
  box-shadow: var(--hlaia-shadow-hover);
}

/* ---- 卡片标题 ---- */
.card-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--hlaia-text);
  margin: 0 0 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--hlaia-border);
}

/* ---- 用户名只读行 ---- */
.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 4px;
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-surface-light);
}

.info-label {
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  font-weight: 500;
  color: var(--hlaia-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  min-width: 80px;
}

.info-value {
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--hlaia-text);
}

/* ---- 表单样式（与 LoginView 一致） ---- */
.settings-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.settings-form :deep(.el-form-item__label) {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--hlaia-text-muted);
  padding-bottom: 6px;
}

.settings-form :deep(.el-input__wrapper) {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius);
  box-shadow: none;
  padding: 4px 12px;
  transition: all 0.3s ease;
}

.settings-form :deep(.el-input__wrapper:hover) {
  border-color: var(--hlaia-primary-light);
}

.settings-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.15);
}

.settings-form :deep(.el-input__inner) {
  color: var(--hlaia-text);
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
}

.settings-form :deep(.el-input__inner::placeholder) {
  color: var(--hlaia-text-muted);
}

/* 校验错误时的样式 */
.settings-form :deep(.el-form-item.is-error .el-input__wrapper) {
  border-color: var(--hlaia-danger);
  box-shadow: 0 0 0 2px rgba(231, 76, 60, 0.12);
}

.settings-form :deep(.el-form-item__error) {
  font-family: 'DM Sans', sans-serif;
  color: var(--hlaia-danger);
  padding-top: 4px;
}

/* ---- 操作按钮 ---- */
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 140px;
  height: 42px;
  padding: 0 24px;
  border-radius: var(--hlaia-radius);
  border: none;
  background: var(--hlaia-primary);
  color: #fff;
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}

.action-btn:hover:not(:disabled) {
  background: var(--hlaia-primary-dark);
  box-shadow: 0 4px 16px rgba(74, 127, 199, 0.25);
  transform: translateY(-1px);
}

.action-btn:active:not(:disabled) {
  transform: translateY(0);
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ---- Loading 旋转动画 ---- */
.btn-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .settings-content {
    padding: 20px 16px 40px;
  }

  .settings-card {
    padding: 20px;
  }

  .info-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
