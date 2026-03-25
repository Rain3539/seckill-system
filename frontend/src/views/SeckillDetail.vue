<template>
  <div class="page-container" v-loading="loading">
    <el-button text @click="$router.back()" class="back-btn">
      <el-icon><ArrowLeft /></el-icon> 返回秒杀专区
    </el-button>

    <template v-if="sp">
      <el-row :gutter="40" class="detail-layout">
        <!-- 左：图片 -->
        <el-col :xs="24" :md="10">
          <div class="img-box">
            <img v-if="sp.imageUrl" :src="sp.imageUrl" :alt="sp.name" class="product-img" />
            <el-icon v-else size="120" color="#f56c6c"><Present /></el-icon>
          </div>
          <!-- 状态标签 -->
          <div class="status-strip" :class="actStatus">
            <el-icon><Timer /></el-icon>
            <span v-if="actStatus==='upcoming'">距开始：{{ countdown }}</span>
            <span v-else-if="actStatus==='active'">距结束：{{ countdown }}</span>
            <span v-else-if="actStatus==='soldout'">已售罄</span>
            <span v-else>活动已结束</span>
          </div>
        </el-col>

        <!-- 右：信息 -->
        <el-col :xs="24" :md="14">
          <div class="badge-row">
            <el-tag type="danger" size="large" effect="dark">⚡ 限时秒杀</el-tag>
            <el-tag :type="tagType" size="large">{{ statusLabel }}</el-tag>
          </div>

          <h1 class="product-name">{{ sp.name }}</h1>

          <div class="price-box">
            <div>
              <div class="price-label">秒杀价</div>
              <div class="seckill-price">¥{{ sp.seckillPrice }}</div>
            </div>
            <div class="divider-v"></div>
            <div>
              <div class="price-label">原价</div>
              <div class="origin-price">¥{{ sp.originPrice }}</div>
            </div>
            <div class="divider-v"></div>
            <div>
              <div class="price-label">折扣</div>
              <div class="discount">{{ discount }}折</div>
            </div>
          </div>

          <el-divider />

          <!-- 库存进度 -->
          <div class="stock-row">
            <span class="stock-label">剩余库存</span>
            <el-progress
              :percentage="stockPct"
              :color="stockColor"
              :stroke-width="14"
              style="flex:1"
            />
            <span class="stock-num">{{ sp.availStock }} / {{ sp.totalStock }}</span>
          </div>

          <!-- 时间信息 -->
          <div class="time-info">
            <div><span class="tl">开始时间：</span><span class="tv">{{ fmtTime(sp.startTime) }}</span></div>
            <div><span class="tl">结束时间：</span><span class="tv">{{ fmtTime(sp.endTime) }}</span></div>
          </div>

          <el-divider />

          <div class="desc-box">
            <h3>活动说明</h3>
            <p>{{ sp.description }}</p>
            <el-alert type="info" :closable="false" style="margin-top:12px">
              每位用户每件秒杀商品仅限购买 <strong>1次</strong>，请登录后参与秒杀。
            </el-alert>
          </div>

          <div class="action-row">
            <el-button
              type="danger" size="large"
              :disabled="actStatus !== 'active'"
              :loading="buying"
              @click="handleSeckill"
            >
              <el-icon><Lightning /></el-icon>
              {{ actStatus === 'active' ? '立即秒杀' : statusLabel }}
            </el-button>
          </div>
        </el-col>
      </el-row>
    </template>

    <el-empty v-else-if="!loading" description="商品不存在" />

    <!-- 秒杀结果弹窗 -->
    <el-dialog v-model="orderDialogVisible" :title="orderProcessing ? '⏳ 订单处理中' : '🎉 秒杀成功！'" width="420px" center>
      <div class="order-success" v-if="orderProcessing">
        <el-icon size="64" color="#409eff"><Loading /></el-icon>
        <p class="sub" style="margin-top:16px">秒杀请求已提交，订单正在处理中...</p>
        <p class="sub">请稍候，系统正在为您创建订单</p>
      </div>
      <div class="order-success" v-else>
        <el-icon size="64" color="#f56c6c"><CircleCheckFilled /></el-icon>
        <p class="sub">订单号</p>
        <p class="order-no">{{ createdOrder?.orderNo }}</p>
        <p class="amount">秒杀价：<strong>¥{{ createdOrder?.amount }}</strong></p>
        <el-alert type="warning" :closable="false" style="margin-top:12px" show-icon>
          请在24小时内完成支付，逾期自动取消
        </el-alert>
      </div>
      <template #footer>
        <template v-if="orderProcessing">
          <el-button @click="orderDialogVisible=false">关闭</el-button>
        </template>
        <template v-else>
          <el-button @click="orderDialogVisible=false; $router.push('/orders')">查看订单</el-button>
          <el-button type="danger" :loading="paying" @click="handlePay">立即支付</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Present, Timer, CircleCheckFilled, Loading } from '@element-plus/icons-vue'
import { getSeckillDetailApi, doSeckillApi, getSeckillOrderApi } from '../api/seckill.js'
import { payOrderApi } from '../api/order.js'
import { useUserStore } from '../store/index.js'

// Lightning 图标 fallback（element-plus 有些版本叫不同名字）
const Lightning = { render: () => null }

const route     = useRoute()
const router    = useRouter()
const userStore = useUserStore()

const sp                 = ref(null)
const loading            = ref(false)
const buying             = ref(false)
const paying             = ref(false)
const orderDialogVisible = ref(false)
const createdOrder       = ref(null)
const orderProcessing    = ref(false)
const countdown          = ref('')
let   timer              = null

onMounted(async () => {
  loading.value = true
  try {
    const res = await getSeckillDetailApi(route.params.id)
    sp.value = res.data
    startCountdown()
  } finally {
    loading.value = false
  }
})
onUnmounted(() => clearInterval(timer))

// ── 倒计时 ────────────────────────────────────────────
function startCountdown() {
  const tick = () => {
    if (!sp.value) return
    const now   = Date.now()
    const start = new Date(sp.value.startTime).getTime()
    const end   = new Date(sp.value.endTime).getTime()
    const target = now < start ? start : end
    const diff   = Math.max(0, target - now)
    const h = Math.floor(diff / 3600000)
    const m = Math.floor((diff % 3600000) / 60000)
    const s = Math.floor((diff % 60000) / 1000)
    countdown.value = `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`
  }
  tick()
  timer = setInterval(tick, 1000)
}

// ── 计算属性 ──────────────────────────────────────────
const actStatus = computed(() => {
  if (!sp.value) return 'ended'
  const now = Date.now()
  const s   = new Date(sp.value.startTime).getTime()
  const e   = new Date(sp.value.endTime).getTime()
  if (now < s)              return 'upcoming'
  if (now > e)              return 'ended'
  if (sp.value.availStock <= 0) return 'soldout'
  return 'active'
})
const statusLabel = computed(() =>
  ({ active:'进行中', upcoming:'即将开始', ended:'已结束', soldout:'已售罄' })[actStatus.value])
const tagType = computed(() =>
  ({ active:'success', upcoming:'primary', ended:'info', soldout:'danger' })[actStatus.value])
const discount = computed(() =>
  sp.value ? (sp.value.seckillPrice / sp.value.originPrice * 10).toFixed(1) : 0)
const stockPct = computed(() =>
  sp.value?.totalStock ? Math.round(sp.value.availStock / sp.value.totalStock * 100) : 0)
const stockColor = computed(() => {
  const p = stockPct.value
  return p > 50 ? '#67c23a' : p > 20 ? '#e6a23c' : '#f56c6c'
})

function fmtTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN') : '-'
}

// ── 秒杀操作 ─────────────────────────────────────────
async function handleSeckill() {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); router.push('/login'); return }
  buying.value = true
  try {
    const res = await doSeckillApi({ seckillProductId: sp.value.id, quantity: 1 })
    // 异步模式：res.data = { messageId, status: "PROCESSING", message }
    orderProcessing.value = true
    createdOrder.value = null
    orderDialogVisible.value = true
    sp.value.availStock = Math.max(0, sp.value.availStock - 1)
    // 轮询查询订单结果
    await pollSeckillOrder(sp.value.id)
  } catch(e) {
    ElMessage.error(e.message || '秒杀失败，请重试')
  } finally {
    buying.value = false
  }
}

/** 轮询查询秒杀订单，最多 30 次（约 30 秒） */
async function pollSeckillOrder(seckillProductId) {
  for (let i = 0; i < 30; i++) {
    await new Promise(r => setTimeout(r, 1000))
    if (!orderDialogVisible.value) return
    try {
      const res = await getSeckillOrderApi(seckillProductId)
      if (res.data) {
        createdOrder.value = res.data
        orderProcessing.value = false
        return
      }
    } catch (_) { /* 订单尚未创建，继续轮询 */ }
  }
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
.page-container { max-width: 1000px; margin: 0 auto; }
.back-btn { margin-bottom: 20px; color: #606266; }
.img-box {
  width: 100%; aspect-ratio: 4/3; background: #fff5f5; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.product-img { width: 100%; height: 100%; object-fit: cover; border-radius: 12px; }
.status-strip {
  display: flex; align-items: center; gap: 6px; justify-content: center;
  padding: 10px; border-radius: 0 0 12px 12px; margin-top: -4px;
  font-size: 14px; font-weight: 600; color: #fff;
}
.status-strip.active   { background: linear-gradient(90deg,#f56c6c,#ff8c42); }
.status-strip.upcoming { background: linear-gradient(90deg,#409eff,#36c5f0); }
.status-strip.ended,
.status-strip.soldout  { background: #909399; }
.badge-row { display: flex; gap: 8px; margin-bottom: 16px; }
.product-name { font-size: 24px; font-weight: 700; line-height: 1.4; margin-bottom: 20px; }
.price-box {
  display: flex; gap: 0; background: #fff5f5; border-radius: 10px;
  padding: 16px; margin-bottom: 16px; align-items: center;
}
.price-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.seckill-price { font-size: 32px; font-weight: 700; color: #f56c6c; }
.origin-price  { font-size: 20px; color: #c0c4cc; text-decoration: line-through; }
.discount      { font-size: 22px; font-weight: 700; color: #e6a23c; }
.divider-v { width: 1px; background: #eee; height: 48px; margin: 0 20px; }
.stock-row { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.stock-label { font-size: 14px; color: #303133; white-space: nowrap; }
.stock-num   { font-size: 13px; color: #f56c6c; white-space: nowrap; }
.time-info { display: flex; flex-direction: column; gap: 6px; margin-bottom: 4px; }
.tl { font-size: 13px; color: #909399; }
.tv { font-size: 13px; color: #303133; font-weight: 500; }
.desc-box h3 { font-size: 16px; margin-bottom: 10px; }
.desc-box p  { font-size: 14px; color: #606266; line-height: 1.8; }
.action-row { margin-top: 20px; }
.action-row .el-button { min-width: 200px; height: 52px; font-size: 18px; font-weight: 700; }
.order-success { text-align: center; padding: 16px 0; }
.sub { color: #909399; font-size: 13px; margin: 12px 0 4px; }
.order-no { font-family: monospace; font-size: 14px; font-weight: 600; }
.amount { margin-top: 12px; font-size: 15px; color: #606266; }
.amount strong { color: #f56c6c; font-size: 22px; }
</style>
