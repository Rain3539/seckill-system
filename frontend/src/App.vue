<template>
  <el-container class="app-wrapper">
    <el-header v-if="!isAuthPage" class="app-header">
      <div class="logo" @click="$router.push('/')">⚡ 秒杀商城</div>
      <el-menu mode="horizontal" :default-active="activeMenu"
               router class="nav-menu" :ellipsis="false">
        <el-menu-item index="/products">🛍️ 商品列表</el-menu-item>
        <el-menu-item index="/seckill">⚡ 秒杀专区</el-menu-item>
        <el-menu-item v-if="userStore.isLoggedIn" index="/orders">
          📦 我的订单
          <el-badge v-if="pendingCount > 0" :value="pendingCount"
                    class="order-badge" />
        </el-menu-item>
      </el-menu>
      <div class="header-right">
        <template v-if="userStore.isLoggedIn">
          <el-tag type="success" size="large">👤 {{ userStore.username }}</el-tag>
          <el-button text type="danger" @click="userStore.logout">退出登录</el-button>
        </template>
        <template v-else>
          <el-button @click="$router.push('/login')">登录</el-button>
          <el-button type="primary" @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>

    <el-main>
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from './store/index.js'
import { getMyOrdersApi } from './api/order.js'

const route     = useRoute()
const userStore = useUserStore()

const isAuthPage = computed(() => ['/login', '/register'].includes(route.path))

// 高亮父路由（详情页归属）
const activeMenu = computed(() => {
  if (route.path.startsWith('/seckill'))  return '/seckill'
  if (route.path.startsWith('/products')) return '/products'
  return route.path
})

// 待支付徽标：登录后获取一次
const pendingCount = ref(0)
watch(() => userStore.isLoggedIn, async (v) => {
  if (v) {
    try {
      const res = await getMyOrdersApi()
      pendingCount.value = (res.data || []).filter(o => o.status === 0).length
    } catch(_) {}
  } else {
    pendingCount.value = 0
  }
}, { immediate: true })
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { background: #f5f7fa; font-family: -apple-system, "PingFang SC", sans-serif; }
.app-wrapper { min-height: 100vh; }
.app-header {
  display: flex; align-items: center; gap: 12px;
  background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,.08);
  padding: 0 28px; position: sticky; top: 0; z-index: 100; height: 60px !important;
}
.logo {
  font-size: 20px; font-weight: 800; color: #f56c6c;
  white-space: nowrap; cursor: pointer; letter-spacing: -0.5px;
}
.nav-menu { border-bottom: none !important; flex: 1; height: 60px; }
.header-right { margin-left: auto; display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.el-main { padding: 28px 24px; }
.order-badge { margin-left: 4px; vertical-align: middle; }

/* 页面切换动画 */
.page-enter-active, .page-leave-active { transition: all .2s ease; }
.page-enter-from { opacity: 0; transform: translateX(16px); }
.page-leave-to   { opacity: 0; transform: translateX(-16px); }
</style>
