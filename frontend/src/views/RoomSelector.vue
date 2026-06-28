<template>
  <div class="room-selector">
    <div class="hero-section">
      <div class="hero-content">
        <h1 class="title">🐢 AI 海龟汤大厅</h1>
        <p class="subtitle">探索真相，推理谜题，与AI一起揭开谜底</p>
      </div>
      <div class="hero-decoration"></div>
    </div>

    <div class="main-content">
      <div class="actions-bar">
        <a-button
            type="primary"
            size="large"
            @click="handleCreateRoom"
            :loading="creating"
            class="create-btn"
        >
          <span class="btn-icon">✨</span>
          创建新房间
        </a-button>
        <a-button
            size="large"
            @click="fetchRooms"
            class="refresh-btn"
        >
          <span class="btn-icon">🔄</span>
          刷新列表
        </a-button>
      </div>

      <div v-if="rooms.length === 0" class="empty-state">
        <div class="empty-icon"></div>
        <p>暂无进行中的房间</p>
        <p class="empty-hint">点击"创建新房间"开始你的第一局游戏吧！</p>
      </div>

      <div v-else class="room-grid">
        <div
            v-for="item in rooms"
            :key="item.roomId"
            class="room-card"
            @click="handleJoin(item.roomId)"
        >
          <div class="room-header">
            <div class="room-status" :class="item.status === 'PLAYING' ? 'playing' : 'waiting'">
              {{ item.status === 'PLAYING' ? '游戏中' : '等待中' }}
            </div>
            <div class="room-id">#{{ item.roomId }}</div>
          </div>

          <div class="room-info">
            <div class="info-item">
              <span class="label">当前轮次</span>
              <span class="value">{{ item.currentTurn }} / {{ item.maxTurns }}</span>
            </div>
            <div class="info-item">
              <span class="label">进度</span>
              <a-progress
                  :percent="Math.round((item.currentTurn / item.maxTurns) * 100)"
                  :show-info="false"
                  :stroke-color="item.status === 'PLAYING' ? '#ff7875' : '#95de64'"
              />
            </div>
          </div>

          <div class="room-footer">
            <span class="join-text">点击进入房间 →</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { createRoom, getActiveRooms } from '../api/chat'
import { message } from 'ant-design-vue'

const router = useRouter()
const rooms = ref([])
const creating = ref(false)

const fetchRooms = async () => {
  try {
    const res = await getActiveRooms()
    rooms.value = res.data || []
  } catch (error) {
    message.error('获取房间列表失败')
  }
}

const handleCreateRoom = async () => {
  creating.value = true
  try {
    const res = await createRoom()
    const roomId = res.data.roomId
    message.success('房间创建成功！')
    router.push(`/chat/${roomId}`)
  } catch (error) {
    message.error('创建房间失败')
  } finally {
    creating.value = false
  }
}

const handleJoin = (roomId) => {
  router.push(`/chat/${roomId}`)
}

onMounted(fetchRooms)
</script>

<style scoped>
.room-selector {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 0 20px 40px;
}

.hero-section {
  position: relative;
  text-align: center;
  padding: 60px 20px 40px;
  color: white;
  overflow: hidden;
}

.hero-content {
  position: relative;
  z-index: 1;
}

.title {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 16px;
  text-shadow: 2px 2px 8px rgba(0, 0, 0, 0.2);
  animation: fadeInDown 0.6s ease-out;
}

.subtitle {
  font-size: 18px;
  opacity: 0.95;
  margin: 0;
  animation: fadeInUp 0.6s ease-out 0.2s both;
}

.main-content {
  max-width: 1200px;
  margin: 0 auto;
  animation: fadeIn 0.8s ease-out 0.4s both;
}

.actions-bar {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 40px;
}

.create-btn {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(245, 87, 108, 0.3);
  transition: all 0.3s ease;
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(245, 87, 108, 0.4);
}

.refresh-btn {
  height: 48px;
  padding: 0 32px;
  font-size: 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 2px solid rgba(255, 255, 255, 0.5);
  transition: all 0.3s ease;
}

.refresh-btn:hover {
  background: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.btn-icon {
  margin-right: 8px;
  font-size: 18px;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  backdrop-filter: blur(10px);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
  animation: bounce 2s infinite;
}

.empty-state p {
  font-size: 18px;
  color: #666;
  margin: 8px 0;
}

.empty-hint {
  font-size: 14px;
  color: #999;
}

.room-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.room-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  border: 2px solid transparent;
}

.room-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
  border-color: rgba(102, 126, 234, 0.3);
}

.room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.room-status {
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  color: white;
}

.room-status.waiting {
  background: linear-gradient(135deg, #95de64 0%, #73d13d 100%);
}

.room-status.playing {
  background: linear-gradient(135deg, #ff7875 0%, #ff4d4f 100%);
}

.room-id {
  font-size: 20px;
  font-weight: 700;
  color: #333;
  font-family: 'Courier New', monospace;
}

.room-info {
  margin-bottom: 20px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.info-item:last-child {
  margin-bottom: 0;
}

.label {
  font-size: 14px;
  color: #666;
}

.value {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.room-footer {
  padding-top: 16px;
  border-top: 1px solid #e8e8e8;
}

.join-text {
  font-size: 14px;
  color: #667eea;
  font-weight: 500;
  transition: color 0.3s ease;
}

.room-card:hover .join-text {
  color: #764ba2;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@media (max-width: 768px) {
  .title {
    font-size: 32px;
  }

  .subtitle {
    font-size: 16px;
  }

  .actions-bar {
    flex-direction: column;
  }

  .create-btn,
  .refresh-btn {
    width: 100%;
  }

  .room-grid {
    grid-template-columns: 1fr;
  }
}
</style>
