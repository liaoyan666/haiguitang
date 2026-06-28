package com.example.haiguitang.service;

import com.example.haiguitang.model.ChatRoom;

import java.util.List;

public interface ChatService {
    /*
    *  和AI对话
    *
    * @param roomId 聊天室id
    * @param message 用户自己输入的消息
    * @return AI 的结果
    * */

    String dochat(long roomId, String message);


    /*
    * 获取对话列表
    *
    * @return 聊天室列表
    * */
    List<ChatRoom> getChatRoomList();
}
