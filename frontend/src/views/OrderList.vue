<template>
  <div class="page-container">
    <div class="page-header">
      <h2>📦 我的订单</h2>
      <el-button type="primary" plain @click="$router.push('/products')">
        继续购物
      </el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="⚡ 秒杀订单" name="seckill" />
      <el-tab-pane label="🛍️ 普通订单" name="normal" />
      <el-tab-pane name="pending">
        <template #label>
          待支付
          <el-badge v-if="pendingCount > 0" :value="pendingCount" class="tab-badge" />
        </template>
      </el-tab-pane>
      <el-tab-pane label="已支付" name="paid" />
    </el-tabs>

    <div v-loading="loading">
      <transition-group name="fade" tag="div">
        <el-card
          v-for="order in filteredOrders"
          :key="order.orderNo"
          class="order-card"
          shadow="never"
        >
          <!-- 订单头 -->
          <div class="order-header">
            <div class="left">
              <el-tag :type="order.productType === 1 ? 'danger' : 'primary'"
                      size="small" effect="plain">
                {{ order.productType === 1 ? '⚡ 秒杀' : '🛍️ 普通' }}
              </el-tag>
              <span class="order-no">{{ order.orderNo }}</span>
            </div>
            <el-tag :type="statusType(order.status)" effect="light" size="large">
              {{ statusLabel(order.status) }}
            </el-tag>
          </div>

          <el-divider style="margin: 10px 0" />

          <!-- 订单内容 -->
          <div class="order-body">
            <div class="product-icon" :class="order.productType === 1 ? 'icon-seckill' : 'icon-normal'">
              <el-icon size="32" color="#fff">
                <Present v-if="order.productType === 1" />
                <Goods   v-else />
              </el-icon>
            </div>
            <div class="product-info">
              <div class="product-name">{{ order.productName }}</div>
              <div class="product-meta">
                ¥{{ order.unitPrice }} × {{ order.quantity }} 件
              </div>
            </div>
            <div class="order-amount">
              <div class="amount-label">实付金额</div>
              <div class="amount-value">¥{{ order.amount }}</div>
            </div>
          </div>

          <!-- 订单底 -->
          <div class="order-footer">
            <span class="order-time">{{ fmtTime(order.createdAt) }}</span>
            <div class="order-actions" v-if="order.status === 0">
              <el-button
                type="primary" size="small"
                :loading="payingNo === order.orderNo"
                @click="handlePay(order)"
              >
                💳 立即支付
              </el-button>
              <el-button
                size="small" type="danger" plain
                :loading="cancelingNo === order.orderNo"
                @click="handleCancel(order)"
              >
                取消订单
              </el-button>
            </div>
            <div v-else-if="order.status === 1" class="paid-hint">
              <el-icon color="#67c23a"><CircleCheckFilled /></el-icon>
              已支付
            </div>
          </div>
        </el-card>
      </transition-group>

      <el-empty
        v-if="!loading && filteredOrders.length === 0"
        description="暂无订单记录"
        style="margin-top:40px"
      >
        <el-button type="primary" @click="$router.push('/products')">去逛逛</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Goods, Present, CircleCheckFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyOrdersApi, payOrderApi, cancelOrderApi } from '../api/order.js'

const orders      = ref([])
const loading     = ref(false)
const activeTab   = ref('all')
const payingNo    = ref('')
const cancelingNo = ref('')

const filteredOrders = computed(() => {
  switch (activeTab.value) {
    case 'seckill': return orders.value.filter(o => o.productType === 1)
    case 'normal':  return orders.value.filter(o => o.productType === 0)
    case 'pending': return orders.value.filter(o => o.status === 0)
    case 'paid':    return orders.value.filter(o => o.status === 1)
    default:        return orders.value
  }
})

const pendingCount = computed(() => orders.value.filter(o => o.status === 0).length)

onMounted(fetchOrders)

async function fetchOrders() {
  loading.value = true
  try {
    const res = await getMyOrdersApi()
    orders.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handlePay(order) {
  payingNo.value = order.orderNo
  try {
    await payOrderApi(order.orderNo)
    ElMessage.success('支付成功！')
    order.status = 1
  } catch(e) {
    ElMessage.error(e.message || '支付失败')
  } finally {
    payingNo.value = ''
  }
}

async function handleCancel(order) {
  try {
    await ElMessageBox.confirm('确认取消该订单？取消后库存会自动释放。', '取消订单', {
      type: 'warning', confirmButtonText: '确认取消', confirmButtonClass: 'el-button--danger'
    })
    cancelingNo.value = order.orderNo
    await cancelOrderApi(order.orderNo)
    ElMessage.success('订单已取消')
    order.status = 2
  } catch(e) {
    if (e !== 'cancel') ElMessage.error(e.message || '取消失败')
  } finally {
    cancelingNo.value = ''
  }
}

const statusLabel = s => ({ 0:'待支付', 1:'已支付', 2:'已取消' }[s] ?? '未知')
const statusType  = s => ({ 0:'warning', 1:'success', 2:'info' }[s] ?? '')

function fmtTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN') : '-'
}
</script>

<style scoped>
.page-container { max-width: 900px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 22px; }
.tab-badge { margin-left: 4px; vertical-align: middle; }
.order-card { margin-bottom: 14px; border-radius: 10px; transition: box-shadow .2s; }
.order-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.1) !important; }
.order-header { display: flex; justify-content: space-between; align-items: center; }
.left { display: flex; align-items: center; gap: 10px; }
.order-no { font-family: monospace; font-size: 13px; color: #909399; }
.order-body { display: flex; align-items: center; gap: 16px; padding: 4px 0; }
.product-icon {
  width: 60px; height: 60px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.icon-seckill { background: linear-gradient(135deg, #f56c6c, #ff8c42); }
.icon-normal  { background: linear-gradient(135deg, #409eff, #36c5f0); }
.product-info { flex: 1; min-width: 0; }
.product-name { font-weight: 600; font-size: 15px; margin-bottom: 4px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.product-meta { font-size: 13px; color: #909399; }
.order-amount { text-align: right; flex-shrink: 0; }
.amount-label { font-size: 12px; color: #909399; margin-bottom: 2px; }
.amount-value { font-size: 22px; font-weight: 700; color: #f56c6c; }
.order-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
.order-time { font-size: 12px; color: #c0c4cc; }
.order-actions { display: flex; gap: 8px; }
.paid-hint { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #67c23a; font-weight: 600; }
.fade-enter-active, .fade-leave-active { transition: all .3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
