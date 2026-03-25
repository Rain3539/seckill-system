<template>
  <div class="page-container">
    <div class="seckill-banner">
      <h2>⚡ 秒杀专区</h2>
      <p>限时限量 · 每人限购1件 · 先到先得</p>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :md="8" v-for="sp in seckillProducts" :key="sp.id">
        <el-card class="seckill-card" shadow="hover">
          <div class="status-badge" :class="sp.activityStatus">{{ statusText(sp) }}</div>

          <!-- 商品图片（Nginx静态服务） -->
          <div class="product-img" @click="goDetail(sp.id)">
            <img v-if="sp.imageUrl" :src="sp.imageUrl" :alt="sp.name" class="img" />
            <el-icon v-else size="72" color="#f56c6c"><Present /></el-icon>
          </div>

          <div class="product-name" @click="goDetail(sp.id)">{{ sp.name }}</div>

          <div class="price-row">
            <span class="seckill-price">¥{{ sp.seckillPrice }}</span>
            <span class="origin-price">¥{{ sp.originPrice }}</span>
            <el-tag type="danger" size="small">{{ discount(sp) }}折</el-tag>
          </div>

          <div class="stock-bar">
            <span class="slabel">库存</span>
            <el-progress :percentage="stockPct(sp)" :color="stockColor(sp)"
                         :stroke-width="10" style="flex:1" :show-text="false"/>
            <span class="snum">剩 {{ sp.availStock }}</span>
          </div>

          <div class="time-row">
            <el-icon><Timer /></el-icon>
            <span v-if="sp.activityStatus==='upcoming'">{{ fmtTime(sp.startTime) }} 开始</span>
            <span v-else-if="sp.activityStatus==='active'">截止 {{ fmtTime(sp.endTime) }}</span>
            <span v-else>活动已结束</span>
          </div>

          <div class="btn-row">
            <el-button size="small" @click="goDetail(sp.id)">查看详情</el-button>
            <el-button type="danger" size="small"
                       :disabled="sp.activityStatus !== 'active'"
                       :loading="buyingId === sp.id"
                       @click="handleSeckill(sp)">
              {{ sp.activityStatus === 'active' ? '立即秒杀' : statusText(sp) }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && seckillProducts.length === 0" description="暂无秒杀活动" />

    <!-- 秒杀结果弹窗 -->
    <el-dialog v-model="orderDialogVisible" :title="orderProcessing ? '⏳ 订单处理中' : '🎉 秒杀成功！'" width="420px" center>
      <div class="order-success" v-if="orderProcessing">
        <el-icon size="60" color="#409eff"><Loading /></el-icon>
        <p class="sub" style="margin-top:16px">秒杀请求已提交，订单正在处理中...</p>
        <p class="sub">请稍候，系统正在为您创建订单</p>
      </div>
      <div class="order-success" v-else>
        <el-icon size="60" color="#f56c6c"><CircleCheckFilled /></el-icon>
        <p class="sub">订单号</p>
        <p class="order-no">{{ createdOrder?.orderNo }}</p>
        <p class="amount">秒杀价：<strong>¥{{ createdOrder?.amount }}</strong></p>
        <el-alert type="warning" :closable="false" style="margin-top:12px">
          请在24小时内完成支付，逾期订单将自动取消
        </el-alert>
      </div>
      <template #footer>
        <template v-if="orderProcessing">
          <el-button @click="orderDialogVisible=false">关闭</el-button>
        </template>
        <template v-else>
          <el-button @click="orderDialogVisible=false;$router.push('/orders')">查看订单</el-button>
          <el-button type="danger" :loading="paying" @click="handlePay">立即支付</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Present, Timer, CircleCheckFilled, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/index.js'
import { getSeckillListApi, doSeckillApi, getSeckillOrderApi } from '../api/seckill.js'
import { payOrderApi } from '../api/order.js'

const router          = useRouter()
const userStore       = useUserStore()
const seckillProducts = ref([])
const loading         = ref(false)
const buyingId        = ref(null)
const paying          = ref(false)
const orderDialogVisible = ref(false)
const createdOrder    = ref(null)
const orderProcessing = ref(false)

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    const res = await getSeckillListApi()
    seckillProducts.value = (res.data || []).map(sp => ({
      ...sp, activityStatus: calcStatus(sp)
    }))
  } finally {
    loading.value = false
  }
}

function goDetail(id) { router.push(`/seckill/${id}`) }

function calcStatus(sp) {
  const now = Date.now(), start = new Date(sp.startTime).getTime(), end = new Date(sp.endTime).getTime()
  if (now < start)        return 'upcoming'
  if (now > end)          return 'ended'
  if (sp.availStock <= 0) return 'soldout'
  return 'active'
}
const statusText = sp => ({ active:'进行中', upcoming:'即将开始', ended:'已结束', soldout:'已售罄' })[sp.activityStatus]
const discount   = sp => (sp.seckillPrice / sp.originPrice * 10).toFixed(1)
const stockPct   = sp => sp.totalStock ? Math.round(sp.availStock / sp.totalStock * 100) : 0
const stockColor = sp => { const p = stockPct(sp); return p > 50 ? '#67c23a' : p > 20 ? '#e6a23c' : '#f56c6c' }
function fmtTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit' })
}

async function handleSeckill(sp) {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    await ElMessageBox.confirm(
      `秒杀价 ¥${sp.seckillPrice}（原价 ¥${sp.originPrice}），每人限购1件，确认参与？`,
      `⚡ 秒杀【${sp.name}】`,
      { confirmButtonText: '立即抢购', cancelButtonText: '取消', type: 'warning' }
    )
    buyingId.value = sp.id
    const res = await doSeckillApi({ seckillProductId: sp.id, quantity: 1 })
    // 异步模式：res.data = { messageId, status: "PROCESSING", message }
    orderProcessing.value = true
    createdOrder.value = null
    orderDialogVisible.value = true
    await fetchList() // 刷新库存
    // 轮询查询订单结果
    await pollSeckillOrder(sp.id)
  } catch(e) {
    if (e !== 'cancel') ElMessage.error(e.message || '秒杀失败')
  } finally {
    buyingId.value = null
  }
}

/** 轮询查询秒杀订单，最多 30 次（约 30 秒） */
async function pollSeckillOrder(seckillProductId) {
  for (let i = 0; i < 30; i++) {
    await new Promise(r => setTimeout(r, 1000))
    if (!orderDialogVisible.value) return // 用户已关闭弹窗
    try {
      const res = await getSeckillOrderApi(seckillProductId)
      if (res.data) {
        createdOrder.value = res.data
        orderProcessing.value = false
        return
      }
    } catch (_) { /* 订单尚未创建，继续轮询 */ }
  }
  // 超时仍未查到
  orderProcessing.value = false
  ElMessage.warning('订单处理超时，请在"我的订单"中查看')
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
.seckill-banner { background: linear-gradient(135deg,#ff4e50,#f9d423); border-radius: 12px;
  padding: 28px 32px; margin-bottom: 24px; color: #fff; }
.seckill-banner h2 { font-size: 26px; margin-bottom: 6px; }
.seckill-card { margin-bottom: 20px; position: relative; overflow: hidden; transition: transform .2s; }
.seckill-card:hover { transform: translateY(-4px); }
.status-badge { position: absolute; top:0; right:0; padding:4px 12px; font-size:12px;
  font-weight:600; border-radius:0 0 0 8px; color:#fff; }
.status-badge.active { background:#f56c6c; }
.status-badge.upcoming { background:#409eff; }
.status-badge.ended,.status-badge.soldout { background:#909399; }
.product-img { height:180px; display:flex; align-items:center; justify-content:center;
  background:#fff5f5; border-radius:8px; margin-bottom:10px; overflow:hidden; cursor:pointer; }
.img { width:100%; height:100%; object-fit:cover; transition:transform .3s; }
.img:hover { transform:scale(1.05); }
.product-name { font-weight:700; font-size:15px; margin-bottom:6px; cursor:pointer;
  white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.product-name:hover { color:#f56c6c; }
.price-row { display:flex; align-items:center; gap:8px; margin-bottom:10px; }
.seckill-price { color:#f56c6c; font-size:22px; font-weight:700; }
.origin-price { color:#c0c4cc; font-size:13px; text-decoration:line-through; }
.stock-bar { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
.slabel,.snum { font-size:12px; color:#606266; white-space:nowrap; }
.snum { color:#f56c6c; }
.time-row { display:flex; align-items:center; gap:4px; font-size:12px; color:#909399; margin-bottom:10px; }
.btn-row { display:flex; gap:8px; }
.btn-row .el-button { flex:1; }
.order-success { text-align:center; padding:16px 0; }
.sub { color:#909399; font-size:13px; margin:12px 0 4px; }
.order-no { font-family:monospace; font-size:14px; font-weight:600; }
.amount { margin-top:12px; font-size:15px; color:#606266; }
.amount strong { color:#f56c6c; font-size:20px; }
</style>
