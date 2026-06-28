package com.example.haiguitang.websocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.haiguitang.entity.GameRecord;
import com.example.haiguitang.entity.GameRoom;
import com.example.haiguitang.entity.RoomPlayer;
import com.example.haiguitang.entity.SoupStory;
import com.example.haiguitang.manager.AiManager;
import com.example.haiguitang.mapper.GameRecordMapper;
import com.example.haiguitang.mapper.GameRoomMapper;
import com.example.haiguitang.mapper.RoomPlayerMapper;
import com.example.haiguitang.mapper.SoupStoryMapper;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海龟汤多人游戏 WebSocket 处理器
 * <p>
 * 核心机制：
 * 1. 多人加入同一个房间，共享同一个 AI 主持人
 * 2. 轮流提问：当前只有 currentTurnPlayer 可以发消息，其他人需等待
 * 3. 所有消息（提问+AI回复）广播给房间内所有人
 * 4. 消息持久化到 MySQL
 */
@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    @Resource
    private AiManager aiManager;

    @Resource
    private GameRoomMapper gameRoomMapper;

    @Resource
    private RoomPlayerMapper roomPlayerMapper;

    @Resource
    private GameRecordMapper gameRecordMapper;

    @Resource
    private SoupStoryMapper soupStoryMapper;

    // ==================== 内存数据结构 ====================

    /** 所有在线连接：playerId -> GameSession */
    private static final Map<String, GameSession> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    /** 房间内在线玩家：roomId -> Set<playerId> */
    private static final Map<String, Set<String>> ROOM_PLAYERS = new ConcurrentHashMap<>();

    /** 每个房间的 AI 对话历史：roomId -> List<ChatMessage> */
    private static final Map<String, List<ChatMessage>> ROOM_MESSAGES = new ConcurrentHashMap<>();

    /** 每个房间的 AI 系统预设（含汤面+汤底） */
    private static final Map<String, String> ROOM_SYSTEM_PROMPTS = new ConcurrentHashMap<>();

    /** 当前轮到哪个玩家：roomId -> playerId */
    private static final Map<String, String> CURRENT_TURN = new ConcurrentHashMap<>();

    /** 是否正在等待 AI 回复：roomId -> boolean */
    private static final Map<String, Boolean> AI_THINKING = new ConcurrentHashMap<>();

    // ==================== 连接建立与断开 ====================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) return;

        String path = uri.getPath();
        String[] parts = path.split("/");
        // 路径: /api/ws/game/{roomId}/{playerId}
        String roomId = parts[parts.length - 2];
        String playerId = parts[parts.length - 1];

        // 获取玩家昵称（从查询参数）
        String query = uri.getQuery();
        String playerName = playerId; // 默认用 playerId
        if (query != null && query.contains("name=")) {
            playerName = query.split("name=")[1].split("&")[0];
        }

        GameSession gs = new GameSession(session, roomId, playerId, playerName);
        ONLINE_SESSIONS.put(playerId, gs);

        // 维护房间玩家列表
        ROOM_PLAYERS.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(playerId);

        // 尝试写入 room_player 表
        try {
            if (roomPlayerMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomPlayer>()
                            .eq(RoomPlayer::getRoomId, roomId)
                            .eq(RoomPlayer::getPlayerId, playerId)) == 0) {
                RoomPlayer rp = new RoomPlayer();
                rp.setRoomId(roomId);
                rp.setPlayerId(playerId);
                rp.setPlayerName(playerName);
                roomPlayerMapper.insert(rp);
            }
        } catch (Exception e) {
            log.warn("写入 room_player 失败: {}", e.getMessage());
        }

        log.info("玩家 {} ({}) 加入房间 {}", playerName, playerId, roomId);

        // 广播加入消息
        broadcastToRoom(roomId, buildSystemMsg(playerName + " 加入了房间"), null);

        // 发送当前房间状态
        sendRoomStatus(roomId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        GameSession gs = ONLINE_SESSIONS.values().stream()
                .filter(s -> s.getSession().getId().equals(session.getId()))
                .findFirst().orElse(null);
        if (gs == null) return;

        ONLINE_SESSIONS.remove(gs.getPlayerId());

        Set<String> players = ROOM_PLAYERS.get(gs.getRoomId());
        if (players != null) {
            players.remove(gs.getPlayerId());
            if (players.isEmpty()) {
                ROOM_PLAYERS.remove(gs.getRoomId());
                ROOM_MESSAGES.remove(gs.getRoomId());
                ROOM_SYSTEM_PROMPTS.remove(gs.getRoomId());
                CURRENT_TURN.remove(gs.getRoomId());
                AI_THINKING.remove(gs.getRoomId());
            }
        }

        broadcastToRoom(gs.getRoomId(), buildSystemMsg(gs.getPlayerName() + " 离开了房间"), null);
        sendRoomStatus(gs.getRoomId());
        log.info("玩家 {} ({}) 离开房间 {}", gs.getPlayerName(), gs.getPlayerId(), gs.getRoomId());
    }

    // ==================== 消息处理 ====================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        GameSession gs = ONLINE_SESSIONS.values().stream()
                .filter(s -> s.getSession().getId().equals(session.getId()))
                .findFirst().orElse(null);
        if (gs == null) return;

        String payload = textMessage.getPayload();
        JSONObject json;
        try {
            json = JSONUtil.parseObj(payload);
        } catch (Exception e) {
            sendToPlayer(gs, buildErrorMsg("消息格式错误，请发送 JSON"));
            return;
        }

        String type = json.getStr("type", "chat");
        String content = json.getStr("content", "");

        switch (type) {
            case "start":
                handleStartGame(gs);
                break;
            case "chat":
                handlePlayerChat(gs, content);
                break;
            case "giveup":
                handleGiveUp(gs);
                break;
            case "ready":
                // 玩家准备就绪
                broadcastToRoom(gs.getRoomId(), buildSystemMsg(gs.getPlayerName() + " 已准备"), null);
                break;
            default:
                sendToPlayer(gs, buildErrorMsg("未知的消息类型: " + type));
        }
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 开始游戏：初始化 AI 上下文，随机选择海龟汤故事
     */
    private void handleStartGame(GameSession gs) {
        String roomId = gs.getRoomId();

        // 检查房间是否已开始
        if (ROOM_MESSAGES.containsKey(roomId)) {
            sendToPlayer(gs, buildErrorMsg("游戏已经开始"));
            return;
        }

        // 更新房间状态
        GameRoom gameRoom = gameRoomMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameRoom>()
                        .eq(GameRoom::getRoomId, roomId));
        if (gameRoom != null) {
            gameRoom.setStatus("PLAYING");
            gameRoom.setCurrentTurn(0);
            gameRoomMapper.updateById(gameRoom);
        }

        // 从数据库随机选一个故事
        List<SoupStory> stories = soupStoryMapper.selectList(null);
        SoupStory story = null;
        if (stories != null && !stories.isEmpty()) {
            story = stories.get(new Random().nextInt(stories.size()));
            // 更新游玩次数
            story.setPlayCount(story.getPlayCount() == 0 ? 1 : story.getPlayCount() + 1);
            soupStoryMapper.updateById(story);

            if (gameRoom != null) {
                gameRoom.setStoryId(story.getId());
                gameRoomMapper.updateById(gameRoom);
            }
        }

        // 构建系统预设
        String storyFace = story != null ? story.getSoupFace() : "请 AI 自行编造一个海龟汤谜面";
        //String storyBottom = story != null ? story.getSoupBottom() : "请 AI 自行编造汤底";

        final String systemPrompt = "你是一位神秘且冷静的“海龟汤”游戏主持人。你的职责是引导玩家通过逻辑推理解开谜题，但绝不直接透露答案。\n" +
                "【游戏规则】\n" +
                "1. 开局：当我发送“开始”或“开始游戏”时，你必须立刻给出一个“汤面”（谜题故事,需要符合客观事实逻辑）。\n" +
                "2. 作答限制：在游戏过程中，面对我的任何提问，你只能回答以下三种之一：\n" +
                "  ○ 是（确认属实）\n" +
                "  ○ 否（确认不实）\n" +
                "  ○ 与此无关（问题偏离了当前故事的核心逻辑，或无法用是非回答）。\n" +
                "3. 禁止行为：严禁在推理结束前剧透“汤底”，严禁回答“是/否”之外的解释性文字（除非触发结束条件）。\n" +
                "【游戏结束与复盘】\n" +
                "当出现以下任一情况时，立刻终止问答环节，并公布“汤底”：\n" +
                "1. 玩家放弃：我明确表达了“不想玩了”、“结束游戏”、“退出”或“告诉我答案吧”。\n" +
                "2. 推理成功：我已经完全还原了故事真相，或说出了关键情节。\n" +
                "3. 次数耗尽：我已经进行了 20次 提问，但仍未触及核心真相。";

        ROOM_SYSTEM_PROMPTS.put(roomId, systemPrompt);

        // 初始化消息列表
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMsg = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        messages.add(systemMsg);
        ROOM_MESSAGES.put(roomId, messages);

        // 设置第一个玩家为当前轮次
        Set<String> players = ROOM_PLAYERS.get(roomId);
        if (players != null && !players.isEmpty()) {
            String firstPlayer = players.iterator().next();
            CURRENT_TURN.put(roomId, firstPlayer);
        }

        // 广播游戏开始
        broadcastToRoom(roomId, buildGameMsg("host", "游戏开始！\n\n【汤面】\n" + storyFace + "\n\n请开始提问！"), null);
        sendRoomStatus(roomId);

        log.info("房间 {} 游戏开始，故事ID: {}", roomId, story != null ? story.getId() : "随机");
    }

    /**
     * 处理玩家提问
     */
    private void handlePlayerChat(GameSession gs, String content) {
        String roomId = gs.getRoomId();
        String playerId = gs.getPlayerId();

        // 检查游戏是否已开始
        if (!ROOM_MESSAGES.containsKey(roomId)) {
            sendToPlayer(gs, buildErrorMsg("游戏尚未开始，请发送 {\"type\":\"start\"} 开始游戏"));
            return;
        }

        // 检查是否轮到该玩家
        String currentTurn = CURRENT_TURN.get(roomId);
        if (currentTurn != null && !currentTurn.equals(playerId)) {
            sendToPlayer(gs, buildErrorMsg("还没轮到你，请等待其他玩家提问"));
            return;
        }

        // 检查 AI 是否正在思考
        if (Boolean.TRUE.equals(AI_THINKING.get(roomId))) {
            sendToPlayer(gs, buildErrorMsg("主持人正在思考上一个问题，请稍候..."));
            return;
        }

        // 检查轮次
        GameRoom gameRoom = gameRoomMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameRoom>()
                        .eq(GameRoom::getRoomId, roomId));
        int maxTurns = gameRoom != null && gameRoom.getMaxTurns() != null ? gameRoom.getMaxTurns() : 20;
        int currentTurnNum = gameRoom != null && gameRoom.getCurrentTurn() != null ? gameRoom.getCurrentTurn() : 0;

        if (currentTurnNum >= maxTurns) {
            broadcastToRoom(roomId, buildGameMsg("host", "提问次数已用完！游戏结束。"), null);
            endGame(roomId);
            return;
        }

        // 广播玩家提问
        broadcastToRoom(roomId, buildGameMsg("player", gs.getPlayerName() + "： " + content), gs.getPlayerId());

        // 保存玩家消息到数据库
        saveGameRecord(roomId, gameRoom != null ? gameRoom.getStoryId() : null,
                playerId, gs.getPlayerName(), content, "player", currentTurnNum + 1);

        // 设置 AI 思考中
        AI_THINKING.put(roomId, true);

        // 异步调用 AI
        new Thread(() -> {
            try {
                List<ChatMessage> messages = ROOM_MESSAGES.get(roomId);
                ChatMessage userMsg = ChatMessage.builder().role(ChatMessageRole.USER).content(content).build();
                messages.add(userMsg);

                String answer = aiManager.doChat(messages);

                ChatMessage assistantMsg = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(answer).build();
                messages.add(assistantMsg);

                // 保存 AI 回复到数据库
                saveGameRecord(roomId, gameRoom != null ? gameRoom.getStoryId() : null,
                        "host", "主持人", answer, "host", currentTurnNum + 1);

                // 广播 AI 回复
                broadcastToRoom(roomId, buildGameMsg("host", answer), null);

                // 更新轮次
                if (gameRoom != null) {
                    gameRoom.setCurrentTurn(currentTurnNum + 1);
                    gameRoomMapper.updateById(gameRoom);
                }

                // 检查游戏是否结束
                if (answer.contains("汤底") || answer.contains("推理成功") || answer.contains("次数已用完")) {
                    endGame(roomId);
                } else {
                    // 切换到下一个玩家
                    switchToNextPlayer(roomId);
                }
            } catch (Exception e) {
                log.error("AI 调用失败", e);
                broadcastToRoom(roomId, buildGameMsg("host", "主持人思考时出了点问题，请重试"), null);
            } finally {
                AI_THINKING.put(roomId, false);
            }
        }).start();
    }

    /**
     * 放弃游戏
     */
    private void handleGiveUp(GameSession gs) {
        String roomId = gs.getRoomId();
        if (!ROOM_MESSAGES.containsKey(roomId)) {
            sendToPlayer(gs, buildErrorMsg("游戏尚未开始"));
            return;
        }

        // 让 AI 公布汤底
        List<ChatMessage> messages = ROOM_MESSAGES.get(roomId);
        ChatMessage giveUpMsg = ChatMessage.builder().role(ChatMessageRole.USER).content("我放弃，告诉我答案吧").build();
        messages.add(giveUpMsg);

        try {
            String answer = aiManager.doChat(messages);
            broadcastToRoom(roomId, buildGameMsg("host", answer), null);
        } catch (Exception e) {
            log.error("公布汤底失败", e);
        }

        endGame(roomId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 切换到下一个玩家
     */
    private void switchToNextPlayer(String roomId) {
        Set<String> players = ROOM_PLAYERS.get(roomId);
        if (players == null || players.isEmpty()) return;

        List<String> playerList = new ArrayList<>(players);
        String currentPlayer = CURRENT_TURN.get(roomId);

        int currentIndex = playerList.indexOf(currentPlayer);
        int nextIndex = (currentIndex + 1) % playerList.size();
        String nextPlayer = playerList.get(nextIndex);

        CURRENT_TURN.put(roomId, nextPlayer);

        GameSession nextGs = ONLINE_SESSIONS.get(nextPlayer);
        String nextName = nextGs != null ? nextGs.getPlayerName() : nextPlayer;
        broadcastToRoom(roomId, buildSystemMsg("轮到 " + nextName + " 提问了"), null);
    }

    /**
     * 结束游戏
     */
    private void endGame(String roomId) {
        GameRoom gameRoom = gameRoomMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameRoom>()
                        .eq(GameRoom::getRoomId, roomId));
        if (gameRoom != null) {
            gameRoom.setStatus("FINISHED");
            gameRoomMapper.updateById(gameRoom);
        }

        ROOM_MESSAGES.remove(roomId);
        ROOM_SYSTEM_PROMPTS.remove(roomId);
        CURRENT_TURN.remove(roomId);
        AI_THINKING.remove(roomId);

        broadcastToRoom(roomId, buildSystemMsg("游戏结束！发送 {\"type\":\"start\"} 可重新开始"), null);
    }

    /**
     * 广播消息给房间内所有人
     * @param excludePlayerId 排除的玩家（不发送给自己，null 表示发送给所有人）
     */
    private void broadcastToRoom(String roomId, String message, String excludePlayerId) {
        Set<String> players = ROOM_PLAYERS.get(roomId);
        if (players == null) return;

        for (String playerId : players) {
            if (playerId.equals(excludePlayerId)) continue;
            GameSession gs = ONLINE_SESSIONS.get(playerId);
            if (gs != null && gs.getSession().isOpen()) {
                try {
                    gs.getSession().sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("发送消息给 {} 失败", playerId, e);
                }
            }
        }
    }

    /**
     * 发送消息给指定玩家
     */
    private void sendToPlayer(GameSession gs, String message) {
        try {
            if (gs.getSession().isOpen()) {
                gs.getSession().sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("发送消息给 {} 失败", gs.getPlayerId(), e);
        }
    }

    /**
     * 发送房间状态
     */
    private void sendRoomStatus(String roomId) {
        Set<String> players = ROOM_PLAYERS.get(roomId);
        String currentPlayer = CURRENT_TURN.get(roomId);
        GameRoom gameRoom = gameRoomMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameRoom>()
                        .eq(GameRoom::getRoomId, roomId));

        JSONObject status = JSONUtil.createObj();
        status.set("type", "room_status");

        List<JSONObject> playerList = new ArrayList<>();
        if (players != null) {
            for (String pid : players) {
                GameSession gs = ONLINE_SESSIONS.get(pid);
                JSONObject p = JSONUtil.createObj();
                p.set("playerId", pid);
                p.set("playerName", gs != null ? gs.getPlayerName() : pid);
                p.set("isCurrentTurn", pid.equals(currentPlayer));
                playerList.add(p);
            }
        }
        status.set("players", playerList);
        status.set("currentTurnPlayerId", currentPlayer);
        status.set("gameStarted", ROOM_MESSAGES.containsKey(roomId));
        status.set("currentTurn", gameRoom != null ? gameRoom.getCurrentTurn() : 0);
        status.set("maxTurns", gameRoom != null ? gameRoom.getMaxTurns() : 20);
        status.set("status", gameRoom != null ? gameRoom.getStatus() : "WAITING");

        broadcastToRoom(roomId, status.toString(), null);
    }

    // ==================== 消息构建 ====================

    private String buildGameMsg(String role, String content) {
        JSONObject msg = JSONUtil.createObj();
        msg.set("type", "game_message");
        msg.set("role", role);
        msg.set("content", content);
        msg.set("timestamp", System.currentTimeMillis());
        return msg.toString();
    }

    private String buildSystemMsg(String content) {
        JSONObject msg = JSONUtil.createObj();
        msg.set("type", "system");
        msg.set("content", content);
        msg.set("timestamp", System.currentTimeMillis());
        return msg.toString();
    }

    private String buildErrorMsg(String content) {
        JSONObject msg = JSONUtil.createObj();
        msg.set("type", "error");
        msg.set("content", content);
        msg.set("timestamp", System.currentTimeMillis());
        return msg.toString();
    }

    /**
     * 保存游戏记录到数据库
     */
    private void saveGameRecord(String roomId, Long storyId, String playerId,
                                String playerName, String message, String role, int turnNumber) {
        try {
            GameRecord record = new GameRecord();
            record.setRoomId(roomId);
            record.setStoryId(storyId);
            record.setPlayerId(playerId);
            record.setPlayerName(playerName);
            record.setMessage(message);
            record.setRole(role);
            record.setTurnNumber(turnNumber);
            gameRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("保存游戏记录失败: {}", e.getMessage());
        }
    }
}
