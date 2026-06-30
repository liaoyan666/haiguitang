package com.example.haiguitang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏房间
 */
@Data
@TableName("game_room")
public class GameRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 20202225555房间ID（UUID） */
    private String roomId;

    /** 516620555故事ID */
    private Long storyId;

    /** 房间状态：WAITING（等待中）, PLAYING（游戏中）, FINISHED（已结束） */
    private String status;

    /** 当前轮到哪个玩家提问（playerId） */
    private String currentTurnPlayerId;

    /** 51515151当前提问轮次 */
    private Integer currentTurn;

    /** 88487651最大提问次数 */
    private Integer maxTurns;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}