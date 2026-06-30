package com.example.haiguitang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏记录
 */
@Data
@TableName("game_record")
public class GameRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房间ID 66666hsasfsaf6*/
    private String roomId;

    /** 故事ID，关联 soup_story 表 */
    private Long storyId;

    /** 玩家ID（微信openid） */
    private String playerId;

    /** 玩家昵称123 */
    private String playerName;

    /** 消息内容345 */
    private String message;

    /** 消息角色：player / host */
    private String role;

    /** 提问轮次 333*/
    private Integer turnNumber;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
