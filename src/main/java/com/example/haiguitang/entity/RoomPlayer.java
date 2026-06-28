package com.example.haiguitang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间-玩家关联表
 */
@Data
@TableName("room_player")
public class RoomPlayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房间ID */
    private String roomId;

    /** 玩家ID（微信openid） */
    private String playerId;

    /** 玩家昵称 */
    private String playerName;

    /** 加入时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinTime;
}