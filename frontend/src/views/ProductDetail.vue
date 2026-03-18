<template>
  <div class="page-container" v-loading="loading">
    <el-button text @click="$router.back()" class="back-btn">
      <el-icon><ArrowLeft /></el-icon> 返回商品列表
    </el-button>

    <template v-if="product">
      <el-row :gutter="40" class="detail-layout">
        <!-- 左：商品图片（Nginx静态服务） -->
        <el-col :xs="24" :md="10">
          <div class="img-box">
            <img v-if="product.imageUrl" :src="product.imageUrl"
                 :alt="product.name" class="product-img" />
            <el-icon v-else size="120" color="#c0c4cc"><Goods /></el-icon>
          </div>
        </el-col>

        <!-- 右：商品信息 -->
        <el-col :xs="24" :md="14">
          <h1 class="product-name">{{ product.name }}</h1>

          <div class="price-box">
            <span class="price">¥{{ product.price }}</span>
            <el-tag :type="product.stock > 0 ? 'success' : 'danger'" size="large">
              {{ product.stock > 0 ? `库存充足（${product.stock}件）` : '已售罄' }}
            </el-tag>
          </div>

          <el-divider />

          <div class="desc-box">
            <h3>商品详情</h3>
            <p class="desc-text">{{ product.description }}</p>
          </div>

          <el-divider />

          <!-- 数量选择 -->
          <div class="quantity-row">
            <span class="label">购买数量</span>
            <el-input-number v-model="quantity" :min="1"
                             :max="product.stock" size="large" />
            <span class="stock-hint">（最多 {{ product.stock }} 件）</span>
          </div>

          <!-- 操作按钮 -->
          <div class="action-row">
            <el-button type="primary" size="large"
                       :disabled="product.stock <= 0"
                       :loading="buying"
                       @click="handleBuy">
              🛒 立即下单
            </el-button>
          </div>
        </el-col>
      </el-row>
    </template>

    <el-empty v-else-if="!loading" description="商品不存在" />

    <!-- 下单成功弹窗 -->
    <el-dialog v-model="orderDialogVisible" title="🎉 下单成功" width="420px" center>
      <div class="order-success">
        <el-icon size="60" color="#67c23a"><CircleCheckFilled /></el-icon>
        <p class="sub">订单号</p>
        <p class="order-no">{{ createdOrder?.orderNo }}</p>
        <p class="amount">应付金额：<strong>¥{{ createdOrder?.amount }}</strong></p>
      </div>
      <template #footer>
        <el-button @click="orderDialogVisible = false; $router.push('/orders')">查看订单</el-button>
        <el-button type="primary" :loading="paying" @click="handlePay">立即支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Goods, CircleCheckFilled } from '@element-plus/icons-vue'
import { getProductDetailApi } from '../api/product.js'
import { placeOrderApi, payOrderApi } from '../api/order.js'
import { useUserStore } from '../store/index.js'

const route     = useRoute()
const router    = useRouter()
const userStore = useUserStore()

const product            = ref(null)
const loading            = ref(false)
const buying             = ref(false)
const paying             = ref(false)
const quantity           = ref(1)
const orderDialogVisible = ref(false)
const createdOrder       = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getProductDetailApi(route.params.id)
    product.value = res.data
    if (product.value?.stock > 0) quantity.value = 1
  } finally {
    loading.value = false
  }
})

async function handleBuy() {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); router.push('/login'); return }
  buying.value = true
  try {
    const res = await placeOrderApi({ productId: product.value.id, quantity: quantity.value })
    createdOrder.value = res.data
    orderDialogVisible.value = true
    // 更新本地库存显示
    product.value.stock -= quantity.value
  } catch(e) {
    ElMessage.error(e.message || '下单失败')
  } finally {
    buying.value = false
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
.page-container { max-width: 1000px; margin: 0 auto; }
.back-btn { margin-bottom: 20px; font-size: 14px; color: #606266; }
.detail-layout { margin-top: 8px; }
.img-box {
  width: 100%; aspect-ratio: 4/3; background: #f5f7fa; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.product-img { width: 100%; height: 100%; object-fit: cover; border-radius: 12px; }
.product-name { font-size: 24px; font-weight: 700; line-height: 1.4; margin-bottom: 20px; }
.price-box { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.price { font-size: 36px; font-weight: 700; color: #f56c6c; }
.desc-box h3 { font-size: 16px; color: #303133; margin-bottom: 10px; }
.desc-text { font-size: 14px; color: #606266; line-height: 1.8; }
.quantity-row { display: flex; align-items: center; gap: 12px; margin: 20px 0; }
.label { font-size: 15px; color: #303133; font-weight: 500; white-space: nowrap; }
.stock-hint { font-size: 13px; color: #909399; }
.action-row { display: flex; gap: 12px; margin-top: 8px; }
.action-row .el-button { min-width: 160px; height: 48px; font-size: 16px; }
.order-success { text-align: center; padding: 16px 0; }
.sub { color: #909399; font-size: 13px; margin: 12px 0 4px; }
.order-no { font-family: monospace; font-size: 14px; font-weight: 600; }
.amount { margin-top: 12px; font-size: 15px; color: #606266; }
.amount strong { color: #f56c6c; font-size: 20px; }
</style>
