package com.example.haiguitang.websocket;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * 封装一个 WebSocket 会话信息
 */
@Data
public class GameSession {

    /** WebSocket 原生会话 */
    private WebSocketSession session;

    /** 房间ID */
    private String roomId;

    /** 玩家ID */
    private String playerId;

    /** 玩家昵称 */
    private String playerName;

    public GameSession(WebSocketSession session, String roomId, String playerId, String playerName) {
        this.session = session;
        this.roomId = roomId;
        this.playerId = playerId;
        this.playerName = playerName;
    }
}
