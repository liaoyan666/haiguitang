<template>
  <div class="home-page">
    <div class="content-wrapper">
      <h1 class="title">AI 海龟汤</h1>
      <a-button type="primary" size="large" @click="startGame">
        点击开始
      </a-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { generateRoomId } from '../utils/helper'

const router = useRouter()

const startGame = () => {
  const roomId = generateRoomId()
  router.push(`/chat/${roomId}`)
}
</script>

<style scoped>
.home-page {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.content-wrapper {
  text-align: center;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
}

.title {
  font-size: 48px;
  color: #333;
  margin-bottom: 40px;
  font-weight: bold;
}
</style>
