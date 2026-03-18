import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const userId = ref(localStorage.getItem('userId') || null)

  const isLoggedIn = computed(() => !!token.value)

  function setUser(data) {
    token.value = data.token
    username.value = data.username
    userId.value = data.id
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('userId', data.id)
  }

  function logout() {
    token.value = ''
    username.value = ''
    userId.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('userId')
    window.location.href = '/login'
  }

  return { token, username, userId, isLoggedIn, setUser, logout }
})
