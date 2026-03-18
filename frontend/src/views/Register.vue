<template>
  <div class="split-layout reverse">
    <div class="form-section">
      <div class="form-container">
        <div class="form-header">
          <h2>创建账号</h2>
          <p>开启限时秒杀，抢购全球爆款</p>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="3-20位字符" prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="6-30位字符" show-password prefix-icon="Lock" />
          </el-form-item>
          
          <div class="flex-row">
            <el-form-item label="邮箱" prop="email" class="flex-item">
              <el-input v-model="form.email" placeholder="可选" prefix-icon="Message" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone" class="flex-item">
              <el-input v-model="form.phone" placeholder="可选" prefix-icon="Iphone" />
            </el-form-item>
          </div>

          <el-button type="primary" :loading="loading" @click="handleRegister" class="seckill-btn">
            立即注册
          </el-button>

          <div class="link-row">
            <span class="muted-text">已有账号？</span>
            <el-link type="danger" :underline="false" @click="$router.push('/login')">返回登录</el-link>
          </div>
        </el-form>
      </div>
    </div>

    <div class="illustration-section">
      <div class="glass-container">
        <img src="../../../nginx/static/images/seckill-login.png" alt="Flash Sale" class="main-img" />
        <div class="dynamic-badge">⚡ 优先发货权限</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Message, Iphone } from '@element-plus/icons-vue'
import { registerApi } from '../api/user.js'
import { useUserStore } from '../store/index.js'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '', email: '', phone: '' })
const rules = {
  username: [{ required: true, min: 3, max: 20, message: '用户名3-20位', trigger: 'blur' }],
  password: [{ required: true, min: 6, max: 30, message: '密码6-30位', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

async function handleRegister() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await registerApi(form)
    userStore.setUser(res.data)
    ElMessage.success('注册成功，已自动登录')
    router.push('/products')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 共用样式直接复用 Login.vue 的样式逻辑 */
.split-layout {
  display: flex;
  min-height: 100vh;
  background-color: #ffffff;
}

.reverse {
  flex-direction: row-reverse;
}

.illustration-section {
  flex: 1.2;
  background-color: #fff5f2;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.main-img {
  width: 85%;
  max-width: 600px;
  animation: float 4s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-15px); }
}

.dynamic-badge {
  position: absolute;
  bottom: 40px;
  right: 40px;
  background: white;
  padding: 10px 20px;
  border-radius: 30px;
  font-weight: bold;
  color: #ff4d4f;
  box-shadow: 0 10px 20px rgba(0,0,0,0.05);
}

.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.form-container {
  width: 100%;
  max-width: 440px;
}

.form-header {
  margin-bottom: 30px;
}

.form-header h2 {
  font-size: 32px;
  font-weight: 700;
  color: #1a1a1a;
}

.flex-row {
  display: flex;
  gap: 15px;
}

.flex-item {
  flex: 1;
}

:deep(.el-input__wrapper) {
  background-color: #f5f5f5 !important;
  box-shadow: none !important;
  border-radius: 8px;
  height: 45px;
}

:deep(.el-input__wrapper.is-focus) {
  background-color: #fff !important;
  border: 1px solid #ff4d4f !important;
}

.seckill-btn {
  width: 100%;
  height: 50px;
  background: linear-gradient(135deg, #ff7875 0%, #ff4d4f 100%) !important;
  border: none !important;
  box-shadow: 0 8px 20px rgba(255, 77, 79, 0.3);
  margin-top: 10px;
  font-weight: bold;
}

.link-row {
  margin-top: 20px;
  text-align: center;
}
</style>