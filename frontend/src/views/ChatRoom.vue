<template>
  <div class="chat-room">
    <!-- 顶部导航与信息 -->
    <div class="chat-header">
      <a-button @click="$router.push('/')">⬅️ 返回大厅</a-button>
      <span class="room-id">房间: {{ roomId }}</span>
      <a-tag v-if="currentTurnPlayer" color="blue">
        👤 {{ currentTurnPlayer }} 的回合
      </a-tag>
      <a-button type="link" @click="showStoryDetail" v-if="currentStory && !gameEndedByDetail">
        📖 查看故事详情
      </a-button>
    </div>

    <!-- 消息展示区域 -->
    <div class="message-container" ref="messageContainer">
      <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message-item', msg.role === 'host' ? 'ai-message' : msg.role === 'player' ? 'user-message' : 'system-message']"
      >
        <div class="message-avatar">
          {{ msg.role === 'host' ? '' : msg.role === 'player' ? '' : 'ℹ️' }}
        </div>
        <div class="message-bubble">{{ msg.content }}</div>
      </div>
      <div v-if="isWaiting" class="loading-indicator">
        <a-spin size="small" /> 主持人正在思考...
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="input-area">
      <!-- 汤面展示与控制按钮 -->
      <div v-if="currentStory && !gameStarted" class="story-preview">
        <h3>{{ currentStory.title }}</h3>
        <p>{{ currentStory.soupFace }}</p>
        <a-button type="primary" @click="startGame">🚀 开始游戏</a-button>
      </div>

      <div v-else-if="gameStarted" class="game-controls">
        <a-input
            v-model:value="inputMessage"
            placeholder="请输入你的提问..."
            @pressEnter="sendMessage"
            :disabled="isWaiting || !isMyTurn"
        />
        <a-button type="primary" @click="sendMessage" :disabled="isWaiting || !isMyTurn">发送</a-button>
        <a-button danger @click="endGame">结束游戏</a-button>
      </div>

      <!-- 汤面选择器 (功能点2) -->
      <div v-if="!gameStarted" class="soup-selector">
        <a-select
            v-model:value="selectedSoupId"
            style="width: 200px"
            placeholder="选择海龟汤"
            @change="handleSoupSelect"
        >
          <a-select-option v-for="soup in soupOptions" :key="soup.id" :value="soup.id">
            {{ soup.title }}
          </a-select-option>
        </a-select>
        <a-button @click="loadRecommendations">换一批推荐</a-button>
      </div>
    </div>

    <!-- 故事详情弹窗 (功能点5) -->
    <a-modal v-model:open="detailVisible" title="故事详情" :footer="null">
      <p><strong>汤面：</strong>{{ detailStory?.soupFace }}</p>
      <p v-if="detailStory?.soupBottom"><strong>汤底（已揭晓）：</strong>{{ detailStory.soupBottom }}</p>
      <a-alert message="游戏已结束" type="warning" show-icon v-if="gameEndedByDetail" />
    </a-modal>
  </div>
</template>

<script setup>import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getRoomMessages, getRoomInfo, getRecommendSoup, getSoupDetail } from '../api/chat'
import { message } from 'ant-design-vue'

const route = useRoute()
const roomId = route.params.roomId
const messages = ref([])
const inputMessage = ref('')
const gameStarted = ref(false)
const isWaiting = ref(false) // 功能点1：多人互斥锁
const currentStory = ref(null)
const soupOptions = ref([])
const selectedSoupId = ref(null)

// 详情弹窗相关
const detailVisible = ref(false)
const detailStory = ref(null)
const gameEndedByDetail = ref(false)

// 回合制相关
const currentTurnPlayer = ref(null)
const isMyTurn = ref(true)

// WebSocket 连接
let ws = null
let myPlayerId = null

const connectWebSocket = () => {
  myPlayerId = 'player_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
  const playerName = localStorage.getItem('playerName') || ('玩家' + Math.floor(Math.random() * 1000))

  const wsUrl = `ws://localhost:8080/ws/game/${roomId}/${myPlayerId}?name=${encodeURIComponent(playerName)}`
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('WebSocket Connected', { roomId, playerId: myPlayerId, playerName })
    message.success('已连接到游戏房间')
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      console.log('收到消息:', data)

      if (data.type === 'game_message') {
        messages.value.push({ role: data.role, content: data.content, timestamp: data.timestamp })
        scrollToBottom()
        isWaiting.value = false
      } else if (data.type === 'system') {
        messages.value.push({ role: 'system', content: data.content, timestamp: data.timestamp })
        scrollToBottom()
      } else if (data.type === 'room_status') {
        handleRoomStatus(data)
      } else if (data.type === 'error') {
        message.error(data.content)
        isWaiting.value = false
      }
    } catch (e) {
      console.error('解析消息失败:', e)
    }
  }

  ws.onerror = (error) => {
    console.error('WebSocket Error:', error)
    message.error('WebSocket 连接错误')
  }

  ws.onclose = () => {
    console.log('WebSocket Closed')
    message.warning('连接已断开')
  }
}

const handleRoomStatus = (status) => {
  console.log('房间状态更新:', status)

  if (status.gameStarted !== undefined) {
    gameStarted.value = status.gameStarted
  }

  if (status.currentTurnPlayerId) {
    currentTurnPlayer.value = status.players?.find(p => p.playerId === status.currentTurnPlayerId)?.playerName || '未知玩家'
    isMyTurn.value = status.currentTurnPlayerId === myPlayerId
  }
}

// 功能点4：同步历史消息
const loadHistory = async () => {
  try {
    const res = await getRoomMessages(roomId)
    messages.value = res.data.length > 6 ? res.data.slice(-6) : res.data
    scrollToBottom()
  } catch (error) {
    console.error('加载历史消息失败', error)
  }
}

// 功能点2：加载推荐汤面
const loadRecommendations = async () => {
  const res = await getRecommendSoup(5)
  soupOptions.value = res.data.list
}

const handleSoupSelect = (id) => {
  const story = soupOptions.value.find(s => s.id === id)
  if (story) currentStory.value = story
}

const startGame = () => {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    message.error('WebSocket 未连接')
    return
  }

  ws.send(JSON.stringify({ type: 'start' }))
  message.info('正在开始游戏...')
}

const endGame = () => {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    message.error('WebSocket 未连接')
    return
  }

  ws.send(JSON.stringify({ type: 'giveup' }))
  gameStarted.value = false
  message.warning('游戏已结束')
}

const sendMessage = () => {
  if (!inputMessage.value.trim() || isWaiting.value) return

  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      type: 'chat',
      content: inputMessage.value
    }))

    inputMessage.value = ''
    isWaiting.value = true
    scrollToBottom()
  } else {
    message.error('WebSocket 未连接')
  }
}

// 功能点5：查看详情并结束游戏
const showStoryDetail = async () => {
  if (!currentStory.value) return
  const res = await getSoupDetail(currentStory.value.id)
  detailStory.value = res.data.story
  detailVisible.value = true
  gameEndedByDetail.value = true
  gameStarted.value = false
}

const scrollToBottom = () => {
  nextTick(() => {
    const container = document.querySelector('.message-container')
    if (container) container.scrollTop = container.scrollHeight
  })
}

onMounted(() => {
  connectWebSocket()
  loadHistory()
  loadRecommendations()
})
</script>

<style scoped>
.chat-room {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.chat-room::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
      radial-gradient(circle at 20% 50%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
      radial-gradient(circle at 80% 80%, rgba(255, 255, 255, 0.1) 0%, transparent 50%);
  pointer-events: none;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.room-id {
  font-size: 18px;
  font-weight: 600;
  color: #667eea;
}

.message-container {
  flex: 1;
  overflow-y: auto;
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  z-index: 5;
  padding-bottom: 120px;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 70%;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.ai-message {
  align-self: flex-start;
}

.user-message {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.system-message {
  align-self: center;
  max-width: 90%;
}

.message-avatar {
  width: 45px;
  height: 45px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease;
}

.message-avatar:hover {
  transform: scale(1.1);
}

.ai-message .message-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: 3px solid rgba(255, 255, 255, 0.8);
}

.user-message .message-avatar {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  border: 3px solid rgba(255, 255, 255, 0.8);
}

.system-message .message-avatar {
  background: rgba(255, 255, 255, 0.9);
  border: 2px solid rgba(102, 126, 234, 0.3);
}

.message-bubble {
  padding: 14px 20px;
  border-radius: 18px;
  word-wrap: break-word;
  line-height: 1.6;
  font-size: 15px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.message-bubble:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.ai-message .message-bubble {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #333;
}

.user-message .message-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.system-message .message-bubble {
  background: rgba(255, 255, 255, 0.8);
  color: #666;
  font-style: italic;
  text-align: center;
}

.loading-indicator {
  align-self: flex-start;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

.input-area {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 20px 30px;
  border-top: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.story-preview {
  text-align: center;
  padding: 20px;
}

.story-preview h3 {
  margin: 0 0 10px 0;
  color: #667eea;
  font-size: 20px;
}

.story-preview p {
  margin: 0 0 20px 0;
  color: #666;
}

.game-controls {
  display: flex;
  gap: 10px;
}

.soup-selector {
  padding: 10px;
  border-top: 1px solid #eee;
  display: flex;
  gap: 10px;
}

@media (max-width: 768px) {
  .message-item {
    max-width: 85%;
  }

  .chat-header {
    padding: 10px 15px;
  }

  .room-id {
    font-size: 16px;
  }

  .message-container {
    padding: 20px;
    padding-bottom: 120px;
  }

  .input-area {
    padding: 15px 20px;
  }
}
</style>
