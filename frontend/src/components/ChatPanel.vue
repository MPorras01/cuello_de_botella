<template>
  <transition name="slide">
    <aside v-if="chat.open" class="chat-panel">
      <!-- Cabecera -->
      <header class="chat-head">
        <div class="chat-title">
          <span class="chat-ico">💬</span>
          <h3>Chat con la comunidad</h3>
        </div>
        <button class="x-btn" @click="chat.toggleOpen()" aria-label="Cerrar chat">✕</button>
      </header>

      <!-- Cuerpo: lista de grupos o hilo activo -->
      <div class="chat-body">
        <!-- Columna de grupos -->
        <div class="chat-groups">
          <div class="groups-label">
            <span>Grupos</span>
            <button class="new-group-btn" title="Crear grupo" @click="showNewGroup = !showNewGroup">＋</button>
          </div>

          <form v-if="showNewGroup" class="new-group-form" @submit.prevent="createGroup">
            <input v-model="newName" class="group-input" placeholder="Nombre del grupo" maxlength="60" required />
            <input v-model="newDesc" class="group-input" placeholder="Descripción (opcional)" maxlength="300" />
            <button type="submit" class="group-submit" :disabled="creating">
              {{ creating ? '…' : 'Crear' }}
            </button>
          </form>

          <ul class="group-list">
            <li
              v-for="g in chat.groups"
              :key="g.id"
              class="group-row"
              :class="{ active: g.id === chat.activeGroupId }"
              @click="chat.selectGroup(g.id)"
            >
              <span class="group-avatar">#</span>
              <span class="group-name">{{ g.name }}</span>
            </li>
          </ul>
        </div>

        <!-- Hilo de mensajes -->
        <div v-if="chat.activeGroup" class="chat-thread">
          <div class="thread-head">
            <strong>#{{ chat.activeGroup.name }}</strong>
            <span class="thread-desc">{{ chat.activeGroup.description }}</span>
          </div>

          <div ref="msgList" class="msg-list">
            <p v-if="!chat.messages.length" class="msg-empty">
              Sin mensajes aún — ¡escribe el primero! ✍️
            </p>
            <div
              v-for="m in chat.messages"
              :key="m.id"
              class="msg-row"
              :class="{ mine: m.senderUsername === auth.username }"
            >
              <div class="msg-bubble">
                <span class="msg-author">{{ m.senderUsername }}</span>
                <span class="msg-text">{{ m.content }}</span>
                <span class="msg-time">{{ fmtTime(m.createdAt) }}</span>
              </div>
            </div>
          </div>

          <form class="msg-form" @submit.prevent="send">
            <input
              v-model="draft"
              class="msg-input"
              placeholder="Escribe un mensaje…"
              maxlength="1000"
            />
            <button type="submit" class="msg-send" :disabled="!draft.trim() || sending">➤</button>
          </form>
        </div>

        <div v-else class="thread-placeholder">Selecciona un grupo para chatear</div>
      </div>
    </aside>
  </transition>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { useChatStore } from '../stores/chatStore'
import { useAuthStore } from '../stores/authStore'

const chat = useChatStore()
const auth = useAuthStore()

const draft = ref('')
const sending = ref(false)
const showNewGroup = ref(false)
const newName = ref('')
const newDesc = ref('')
const creating = ref(false)
const msgList = ref(null)

async function send() {
  if (!draft.value.trim() || sending.value) return
  sending.value = true
  try {
    await chat.sendMessage(draft.value)
    draft.value = ''
  } catch { /* el store deja el error */ } finally {
    sending.value = false
  }
}

async function createGroup() {
  if (!newName.value.trim() || creating.value) return
  creating.value = true
  try {
    await chat.createGroup(newName.value.trim(), newDesc.value.trim())
    newName.value = ''
    newDesc.value = ''
    showNewGroup.value = false
  } catch { /* silencioso */ } finally {
    creating.value = false
  }
}

/** Bajar el scroll al final cuando llegan mensajes nuevos. */
watch(
  () => chat.messages.length,
  () => nextTick(() => {
    if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight
  })
)

function fmtTime(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString('es-CO', {
    hour: '2-digit', minute: '2-digit', hour12: false
  })
}
</script>

<style scoped>
.chat-panel {
  position: absolute;
  right: 0.9rem;
  bottom: 9.4rem;
  z-index: 30;
  width: 430px;
  max-width: calc(100% - 1.8rem);
  height: min(560px, calc(100% - 12rem));
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.35);
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.8rem 1rem;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  color: #fff;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.chat-ico { font-size: 1.15rem; }

.chat-title h3 {
  font-family: 'Baloo 2', sans-serif;
  font-size: 0.98rem;
  font-weight: 800;
}

.x-btn {
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  width: 1.8rem;
  height: 1.8rem;
  border-radius: 50%;
  cursor: pointer;
  font-size: 0.8rem;
  transition: background 0.15s;
}

.x-btn:hover { background: rgba(255, 255, 255, 0.3); }

.chat-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* ─── Grupos ─── */
.chat-groups {
  width: 140px;
  flex-shrink: 0;
  border-right: 1px solid #eef2f7;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.groups-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.6rem 0.7rem 0.4rem;
  font-size: 0.66rem;
  font-weight: 900;
  letter-spacing: 0.08em;
  color: #94a3b8;
  text-transform: uppercase;
}

.new-group-btn {
  border: none;
  background: #1a73e8;
  color: #fff;
  width: 1.35rem;
  height: 1.35rem;
  border-radius: 50%;
  font-size: 0.85rem;
  cursor: pointer;
  line-height: 1;
  transition: transform 0.1s;
}

.new-group-btn:hover { transform: scale(1.12); }

.new-group-form {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.3rem 0.6rem 0.5rem;
}

.group-input {
  border: 1.5px solid #e2e8f0;
  border-radius: 8px;
  padding: 0.3rem 0.45rem;
  font-size: 0.68rem;
  font-family: inherit;
  outline: none;
}

.group-input:focus { border-color: #1a73e8; }

.group-submit {
  border: none;
  background: #1a73e8;
  color: #fff;
  border-radius: 8px;
  padding: 0.25rem;
  font-size: 0.66rem;
  font-weight: 800;
  cursor: pointer;
}

.group-submit:disabled { opacity: 0.5; }

.group-list {
  list-style: none;
  overflow-y: auto;
  flex: 1;
  padding: 0.2rem;
}

.group-row {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.5rem 0.55rem;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
}

.group-row:hover { background: #eef2f7; }
.group-row.active { background: #1a73e8; color: #fff; }

.group-avatar {
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 50%;
  background: #e2e8f0;
  color: #64748b;
  display: grid;
  place-items: center;
  font-weight: 900;
  font-size: 0.8rem;
  flex-shrink: 0;
}

.group-row.active .group-avatar { background: rgba(255, 255, 255, 0.25); color: #fff; }

.group-name {
  font-size: 0.72rem;
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ─── Hilo ─── */
.chat-thread {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.thread-head {
  display: flex;
  flex-direction: column;
  padding: 0.55rem 0.85rem;
  border-bottom: 1px solid #eef2f7;
  background: #fff;
}

.thread-head strong {
  font-size: 0.8rem;
  color: #1e293b;
}

.thread-desc {
  font-size: 0.64rem;
  color: #94a3b8;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 0.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  background: #f8fafc;
}

.msg-list::-webkit-scrollbar { width: 6px; }
.msg-list::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }

.msg-empty {
  margin: auto;
  color: #94a3b8;
  font-size: 0.75rem;
  font-weight: 700;
  text-align: center;
}

.msg-row { display: flex; }
.msg-row.mine { justify-content: flex-end; }

.msg-bubble {
  max-width: 85%;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px 14px 14px 4px;
  padding: 0.45rem 0.65rem;
}

.msg-row.mine .msg-bubble {
  background: #1a73e8;
  border-color: #1a73e8;
  border-radius: 14px 14px 4px 14px;
}

.msg-author {
  font-size: 0.62rem;
  font-weight: 900;
  color: #1a73e8;
  text-transform: lowercase;
}

.msg-row.mine .msg-author { color: #bfdbfe; }

.msg-text {
  font-size: 0.78rem;
  color: #1e293b;
  word-break: break-word;
}

.msg-row.mine .msg-text { color: #fff; }

.msg-time {
  font-size: 0.58rem;
  color: #94a3b8;
  align-self: flex-end;
}

.msg-row.mine .msg-time { color: #bfdbfe; }

.msg-form {
  display: flex;
  gap: 0.4rem;
  padding: 0.6rem;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.msg-input {
  flex: 1;
  border: 1.5px solid #e2e8f0;
  border-radius: 999px;
  padding: 0.45rem 0.8rem;
  font-size: 0.78rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.msg-input:focus {
  border-color: #1a73e8;
  box-shadow: 0 0 0 3px rgba(26, 115, 232, 0.12);
}

.msg-send {
  border: none;
  background: linear-gradient(135deg, #1a73e8, #1557c9);
  color: #fff;
  width: 2.2rem;
  height: 2.2rem;
  border-radius: 50%;
  font-size: 0.95rem;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 0.1s, opacity 0.15s;
}

.msg-send:hover { transform: scale(1.08); }
.msg-send:disabled { opacity: 0.4; cursor: default; }

.thread-placeholder {
  flex: 1;
  display: grid;
  place-items: center;
  color: #94a3b8;
  font-size: 0.78rem;
  font-weight: 700;
  background: #f8fafc;
}

/* Transición */
.slide-enter-active, .slide-leave-active { transition: transform 0.25s ease, opacity 0.2s ease; }
.slide-enter-from, .slide-leave-to { transform: translateY(14px); opacity: 0; }
</style>
