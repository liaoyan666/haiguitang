import { createRouter, createWebHistory } from 'vue-router'
import RoomSelector from '../views/RoomSelector.vue'
import ChatRoom from '../views/ChatRoom.vue'

const routes = [
    { path: '/', component: RoomSelector },
    { path: '/chat/:roomId', component: ChatRoom }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router
