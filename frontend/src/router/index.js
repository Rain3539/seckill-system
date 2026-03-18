import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/',                redirect: '/products' },
  { path: '/login',           component: () => import('../views/Login.vue') },
  { path: '/register',        component: () => import('../views/Register.vue') },
  { path: '/products',        component: () => import('../views/ProductList.vue') },
  { path: '/products/:id',    component: () => import('../views/ProductDetail.vue') },
  { path: '/seckill',         component: () => import('../views/SeckillPage.vue') },
  { path: '/seckill/:id',     component: () => import('../views/SeckillDetail.vue') },
  { path: '/orders',          component: () => import('../views/OrderList.vue'), meta: { requiresAuth: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !localStorage.getItem('token')) next('/login')
  else next()
})

export default router
