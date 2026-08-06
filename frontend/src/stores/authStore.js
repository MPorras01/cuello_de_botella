import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_KEY = 'trancones_token'
const USER_KEY = 'trancones_user'

/**
 * Store de autenticación. Persiste el token JWT en localStorage.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) ?? '')
  const username = ref(localStorage.getItem(USER_KEY) ?? '')

  const isAuthenticated = computed(() => !!token.value)

  function setAuth(newToken, newUsername) {
    token.value = newToken
    username.value = newUsername
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, newUsername)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, username, isAuthenticated, setAuth, logout }
})
