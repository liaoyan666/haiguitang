package com.example.haiguitang.model;


import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import lombok.Data;

import java.util.List;

@Data
public class ChatRoom {

    private long roomId;

    private List<ChatMessage> chatMessages;
}
