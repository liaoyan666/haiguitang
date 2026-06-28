package com.example.haiguitang.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;

import java.time.LocalDateTime;

@Data
@TableName("soup_story")
public class SoupStory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String soupFace;

    private String soupBottom;

    private int difficulty;

    private String tags;

    private int playCount;

    private int likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

//    @TableLogic
//    private Integer isDelete;




}
