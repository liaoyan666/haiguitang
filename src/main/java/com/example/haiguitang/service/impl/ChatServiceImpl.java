package com.example.haiguitang.service.impl;


import com.example.haiguitang.global.exception.BusinessException;
import com.example.haiguitang.manager.AiManager;
import com.example.haiguitang.model.ChatRoom;
import com.example.haiguitang.service.ChatService;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatServiceImpl implements ChatService {
    @Resource
    private AiManager aiManager;

    //全局消息映射
    final Map<Long, List<ChatMessage>> globalMessageMap = new HashMap<>();


    /*
     * 聊天
     *
     * @param roomId 聊天室 id
     * @param message 用户自己输入的消息
     * @return
     * */

    @Override
    public String dochat(long roomId, String message) {
        final String systemPrompt = "【角色设定】\n" +
                "你是一位神秘且冷静的“海龟汤”游戏主持人。你的职责是引导玩家通过逻辑推理解开谜题，但绝不直接透露答案。\n" +
                "【游戏规则】\n" +
                "1. 开局：当我发送“开始”或“开始游戏”时，你必须立刻给出一个“汤面”（谜题故事）。\n" +
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

        // 1.准备消息列表
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(message).build();

        List<ChatMessage> messages;

// 如果消息不是“开始”，但该房间还没初始化
        if (!"开始".equals(message) && !globalMessageMap.containsKey(roomId)) {
            throw new BusinessException("请先开始游戏");
        }

// 初始化房间消息
        if ("开始".equals(message)) {
            messages = new ArrayList<>();
            messages.add(systemMessage);
            globalMessageMap.put(roomId, messages);
        } else {
            messages = globalMessageMap.get(roomId);
        }

// 安全添加用户消息
        messages.add(userMessage);

// 2. 调用 AI
        String answer = aiManager.doChat(messages);

        final ChatMessage assistantMessage = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(answer).build();
        messages.add(assistantMessage);

// 3. 返回消息
        if (answer.contains("汤底")) {
            globalMessageMap.remove(roomId);
        }
        return answer;
    }

    @Override
    public List<ChatRoom> getChatRoomList() {
        List<ChatRoom> chatRoomList = new ArrayList<>();
        for (Map.Entry<Long, List<ChatMessage>> roomIdMessageListEntry : globalMessageMap.entrySet()) {
            ChatRoom chatRoom = new ChatRoom();
            chatRoom.setRoomId(roomIdMessageListEntry.getKey());
            chatRoom.setChatMessages(roomIdMessageListEntry.getValue());
            chatRoomList.add(chatRoom);
        }
        return chatRoomList;
    }




}
