import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authFetch } from '../services/api'

/**
 * Store de chat: grupos y mensajes entre usuarios.
 * Polling de mensajes cada 5 s en el grupo activo.
 */
export const useChatStore = defineStore('chat', () => {
  const groups = ref([])
  const activeGroupId = ref(null)
  const messages = ref([])
  const open = ref(false)
  const error = ref('')

  let pollTimer = null

  const activeGroup = computed(() =>
    groups.value.find((g) => g.id === activeGroupId.value) ?? null
  )

  async function fetchGroups() {
    try {
      const res = await authFetch('/api/chat/groups')
      if (res.ok) {
        groups.value = await res.json()
        if (!activeGroupId.value && groups.value.length) {
          activeGroupId.value = groups.value[0].id
        }
        return true
      }
      return false
    } catch {
      return false
    }
  }

  async function createGroup(name, description) {
    const res = await authFetch('/api/chat/groups', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description })
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error ?? body?.message ?? 'No se pudo crear el grupo')
    }
    const group = await res.json()
    groups.value.push(group)
    selectGroup(group.id)
    return group
  }

  async function fetchMessages(groupId) {
    if (groupId == null) return
    try {
      const res = await authFetch(`/api/chat/groups/${groupId}/messages`)
      if (res.ok) {
        messages.value = await res.json()
      }
    } catch { /* silencioso */ }
  }

  async function sendMessage(content) {
    if (!activeGroupId.value || !content.trim()) return null
    const res = await authFetch(`/api/chat/groups/${activeGroupId.value}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: content.trim() })
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error ?? body?.message ?? 'No se pudo enviar el mensaje')
    }
    const message = await res.json()
    messages.value.push(message)
    return message
  }

  function selectGroup(groupId) {
    activeGroupId.value = groupId
    fetchMessages(groupId)
  }

  function toggleOpen() {
    open.value = !open.value
    if (open.value) startPolling()
    else stopPolling()
  }

  function startPolling() {
    stopPolling()
    fetchGroups().then(() => fetchMessages(activeGroupId.value))
    pollTimer = setInterval(() => {
      fetchGroups()
      if (activeGroupId.value) fetchMessages(activeGroupId.value)
    }, 5000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    groups, activeGroupId, activeGroup, messages, open, error,
    fetchGroups, createGroup, fetchMessages, sendMessage,
    selectGroup, toggleOpen, startPolling, stopPolling
  }
})
