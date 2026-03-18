<template>
  <div class="page-container">
    <div class="page-header">
      <h2>🛍️ 商品列表</h2>
      <span class="subtitle">精选优质商品，品质保障</span>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="p in products" :key="p.id">
        <el-card class="product-card" shadow="hover">
          <!-- 商品图片（动静分离：由Nginx直接服务 /static/images/） -->
          <div class="product-img" @click="goDetail(p.id)">
            <img v-if="p.imageUrl" :src="p.imageUrl" :alt="p.name" class="img" />
            <el-icon v-else size="72" color="#c0c4cc"><Goods /></el-icon>
          </div>

          <div class="product-name" @click="goDetail(p.id)">{{ p.name }}</div>
          <div class="product-desc">{{ p.description }}</div>

          <div class="product-footer">
            <span class="product-price">¥{{ p.price }}</span>
            <el-tag :type="p.stock > 0 ? 'success' : 'danger'" size="small">
              {{ p.stock > 0 ? `库存 ${p.stock}` : '已售罄' }}
            </el-tag>
          </div>

          <div class="btn-row">
            <el-button size="small" @click="goDetail(p.id)">查看详情</el-button>
            <el-button
              type="primary" size="small"
              :disabled="p.stock <= 0"
              :loading="buyingId === p.id"
              @click="handleBuy(p)"
            >
              立即下单
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && products.length === 0" description="暂无商品" />

    <!-- 下单成功弹窗：提示去支付 -->
    <el-dialog v-model="orderDialogVisible" title="🎉 下单成功" width="420px" center>
      <div class="order-success">
        <el-icon size="60" color="#67c23a"><CircleCheckFilled /></el-icon>
        <p class="order-no-label">订单号</p>
        <p class="order-no">{{ createdOrder?.orderNo }}</p>
        <p class="order-amount">应付金额：<strong>¥{{ createdOrder?.amount }}</strong></p>
      </div>
      <template #footer>
        <el-button @click="orderDialogVisible = false">稍后支付</el-button>
        <el-button type="primary" :loading="paying" @click="handlePay">立即支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Goods, CircleCheckFilled } from '@element-plus/icons-vue'
import { getProductListApi } from '../api/product.js'
import { placeOrderApi, payOrderApi } from '../api/order.js'
import { useUserStore } from '../store/index.js'

const router    = useRouter()
const userStore = useUserStore()

const products           = ref([])
const loading            = ref(false)
const buyingId           = ref(null)
const orderDialogVisible = ref(false)
const createdOrder       = ref(null)
const paying             = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getProductListApi()
    products.value = res.data || []
  } finally {
    loading.value = false
  }
})

function goDetail(id) { router.push(`/products/${id}`) }

async function handleBuy(p) {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    await ElMessageBox.confirm(`确认购买【${p.name}】×1 件？`, '确认下单',
      { confirmButtonText: '确认下单', cancelButtonText: '取消', type: 'info' })
    buyingId.value = p.id
    const res = await placeOrderApi({ productId: p.id, quantity: 1 })
    createdOrder.value = res.data
    orderDialogVisible.value = true
    // 刷新库存显示
    const listRes = await getProductListApi()
    products.value = listRes.data || []
  } catch(e) {
    if (e !== 'cancel') ElMessage.error(e.message || '下单失败')
  } finally {
    buyingId.value = null
  }
}

async function handlePay() {
  paying.value = true
  try {
    await payOrderApi(createdOrder.value.orderNo)
    ElMessage.success('支付成功！')
    orderDialogVisible.value = false
    router.push('/orders')
  } catch(e) {
    ElMessage.error(e.message || '支付失败')
  } finally {
    paying.value = false
  }
}
</script>

<style scoped>
.page-container { max-width: 1200px; margin: 0 auto; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 22px; }
.subtitle { color: #909399; font-size: 14px; }
.product-card { margin-bottom: 20px; transition: transform .2s; }
.product-card:hover { transform: translateY(-4px); }
.product-img {
  height: 180px; display: flex; align-items: center; justify-content: center;
  background: #f5f7fa; border-radius: 8px; margin-bottom: 12px;
  overflow: hidden; cursor: pointer;
}
.img { width: 100%; height: 100%; object-fit: cover; border-radius: 8px;
  transition: transform .3s; }
.img:hover { transform: scale(1.05); }
.product-name { font-weight: 600; font-size: 15px; margin-bottom: 6px;
  cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.product-name:hover { color: #409eff; }
.product-desc { color: #909399; font-size: 13px; margin-bottom: 10px; height: 38px;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.product-footer { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.product-price { color: #f56c6c; font-size: 20px; font-weight: 700; }
.btn-row { display: flex; gap: 8px; }
.btn-row .el-button { flex: 1; }
.order-success { text-align: center; padding: 16px 0; }
.order-no-label { color: #909399; font-size: 13px; margin: 12px 0 4px; }
.order-no { font-family: monospace; font-size: 14px; font-weight: 600; color: #303133; }
.order-amount { margin-top: 12px; font-size: 15px; color: #606266; }
.order-amount strong { color: #f56c6c; font-size: 20px; }
</style>
