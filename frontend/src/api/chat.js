import axios from 'axios'

const apiClient = axios.create({
    baseURL: '/api',
    timeout: 30000
})

const request = axios.create({
    baseURL: 'http://localhost:8080/api/api/chat', // 确保端口与后端一致
    timeout: 5000
})

export const getChatRooms = () => {
    return apiClient.get('/chat/rooms')
}

export const sendMessage = (roomId, message) => {
    return apiClient.post(`/chat/${roomId}/send`, null, {
        params: { message }
    })
}

// ==================== 海龟汤推荐相关 ====================

// 获取混合推荐故事
export const getRecommendSoup = (count = 3) => {
    return request.get('/soup/recommend', { params: { count } })
}

// 获取热门推荐故事
export const getHotSoup = (count = 5) => {
    return request.get('/soup/hot', { params: { count } })
}

// 随机获取一个故事
export const getRandomSoup = () => {
    return request.get('/soup/random')
}

// 获取故事详情（不返回汤底）
export const getSoupDetail = (id) => {
    return request.get(`/soup/${id}`)
}

// 获取全部故事列表
export const getSoupList = () => {
    return request.get('/soup/list')
}

// ==================== 游戏房间管理相关 ====================

// 创建新房间
export const createRoom = () => {
    return request.post('/room/create')
}

// 获取房间信息
export const getRoomInfo = (roomId) => {
    return request.get(`/room/${roomId}`)
}

// 获取房间历史消息
export const getRoomMessages = (roomId) => {
    return request.get(`/room/${roomId}/messages`)
}

// 获取活跃房间列表
export const getActiveRooms = () => {
    return request.get('/room/active')
}

export default request