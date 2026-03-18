<template>
  <div class="split-layout">
    <div class="illustration-section">
      <div class="glass-container">
        <img src="../../../nginx/static/images/seckill-login.png" alt="Flash Sale" class="main-img" />
        <div class="dynamic-badge">⚡ 抢购火热进行中</div>
      </div>
    </div>

    <div class="form-section">
      <div class="form-container">
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>请输入您的账户信息以开启秒杀之旅</p>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input 
              v-model="form.username" 
              placeholder="请输入用户名" 
              prefix-icon="User"
              class="premium-input"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input 
              v-model="form.password" 
              type="password" 
              placeholder="请输入密码" 
              show-password 
              prefix-icon="Lock"
              class="premium-input"
            />
          </el-form-item>

          <div class="action-footer">
            <el-button type="primary" :loading="loading" @click="handleLogin" class="seckill-btn">
              立即登录
            </el-button>
          </div>

          <div class="link-row">
            <span class="muted-text">还没有账号？</span>
            <el-link type="danger" :underline="false" @click="$router.push('/register')">免费注册</el-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { loginApi } from '../api/user.js'
import { useUserStore } from '../store/index.js'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await loginApi(form)
    userStore.setUser(res.data)
    ElMessage.success('登录成功')
    router.push('/products')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.split-layout {
  display: flex;
  min-height: 100vh;
  background-color: #ffffff;
}

/* 左侧样式 */
.illustration-section {
  flex: 1.2;
  background-color: #fff5f2; /* 匹配图片底色 */
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.main-img {
  width: 85%;
  max-width: 600px;
  filter: drop-shadow(0 20px 30px rgba(0,0,0,0.05));
  animation: float 4s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-15px); }
}

.dynamic-badge {
  position: absolute;
  top: 40px;
  left: 40px;
  background: white;
  padding: 10px 20px;
  border-radius: 30px;
  font-weight: bold;
  color: #ff4d4f;
  box-shadow: 0 10px 20px rgba(255, 77, 79, 0.1);
}

/* 右侧样式 */
.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.form-container {
  width: 100%;
  max-width: 400px;
}

.form-header {
  margin-bottom: 40px;
}

.form-header h2 {
  font-size: 32px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.form-header p {
  color: #8c8c8c;
  font-size: 14px;
}

/* 高端输入框定制 */
:deep(.el-input__wrapper) {
  background-color: #f5f5f5 !important;
  box-shadow: none !important;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 8px 15px;
  transition: all 0.3s;
}

:deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  border-color: #ff4d4f !important;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.1) !important;
}

/* 动感按钮 */
.seckill-btn {
  width: 100%;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff7875 0%, #ff4d4f 100%) !important;
  border: none !important;
  box-shadow: 0 8px 20px rgba(255, 77, 79, 0.3);
  margin-top: 20px;
  transition: all 0.3s;
}

.seckill-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 25px rgba(255, 77, 79, 0.4);
}

.link-row {
  margin-top: 25px;
  text-align: center;
  font-size: 14px;
}

.muted-text {
  color: #8c8c8c;
  margin-right: 8px;
}
</style>