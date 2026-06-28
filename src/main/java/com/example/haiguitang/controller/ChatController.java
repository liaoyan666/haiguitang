package com.example.haiguitang.controller;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.haiguitang.controller.dto.SoupDTO;
import com.example.haiguitang.entity.GameRecord;
import com.example.haiguitang.entity.GameRoom;
import com.example.haiguitang.entity.RoomPlayer;
import com.example.haiguitang.entity.SoupStory;
import com.example.haiguitang.mapper.GameRecordMapper;
import com.example.haiguitang.mapper.GameRoomMapper;
import com.example.haiguitang.mapper.RoomPlayerMapper;
import com.example.haiguitang.model.ChatRoom;
import com.example.haiguitang.service.ChatService;
import com.example.haiguitang.service.SoupStoryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Resource
    private ChatService chatService;

    @Resource
    private SoupStoryService soupStoryService;

    @Resource
    private GameRoomMapper gameRoomMapper;

    @Resource
    private RoomPlayerMapper roomPlayerMapper;

    @Resource
    private GameRecordMapper gameRecordMapper;

    /*
    * 与 AI 对话
    *
    * */

    @PostMapping("/{roomId}/send")
    public String dochat(@PathVariable long roomId, @RequestParam String message) {
        return chatService.dochat(roomId, message);
    }


    /*
    * 获取所有聊天室列表
    *
    * @return 聊天室列表
    * */
    @GetMapping("/rooms")
    public List<ChatRoom> getChatRoomList() {
        return chatService.getChatRoomList();
    }


    // ==================== 海龟汤推荐 ====================

    /**
     * 混合推荐：2条热门 + N-2条随机，打乱返回
     * @param count
     * @return
     */
    @GetMapping("/soup/recommend")
    public SoupDTO recommendSoup(@RequestParam(defaultValue = "3") int count) {
        List<SoupStory> list = soupStoryService.recommend(count);
        return new SoupDTO(list, list.size(), "热门+随机混合推荐");
    }

    /**
     * 热门推荐
     */
    @GetMapping("/soup/hot")
    public Map<String, Object> hotSoup(@RequestParam(defaultValue = "5") int count) {
        List<SoupStory> list = soupStoryService.recommendHot(count);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 随机获取一个故事
     */
    @GetMapping("/soup/random")
    public Map<String, Object> randomSoup() {
        List<SoupStory> list = soupStoryService.recommendRandom(1);
        Map<String, Object> result = new HashMap<>();
        result.put("story", list.isEmpty() ? null : list.get(0));
        return result;
    }

    /**
     * 查看故事详情（不返回汤底）
     */
    @GetMapping("/soup/{id}")
    public Map<String, Object> soupDetail(@PathVariable Long id) {
        SoupStory story = soupStoryService.getById(id);
        if (story != null) {
            story.setSoupBottom(null);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("story", story);
        return result;
    }

    /**
     * 全部故事列表（不返回汤底）
     */
    @GetMapping("/soup/list")
    public Map<String, Object> soupList() {
        List<SoupStory> list = soupStoryService.listAll();
        list.forEach(s -> s.setSoupBottom(null));
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return result;
    }


    // ==================== 游戏房间管理 ====================

    /**
     * 创建新房间
     */
    @PostMapping("/room/create")
    public Map<String, Object> createRoom() {
        String roomId = IdUtil.fastSimpleUUID().substring(0, 8);

        GameRoom room = new GameRoom();
        room.setRoomId(roomId);
        room.setStatus("WAITING");
        room.setCurrentTurn(0);
        room.setMaxTurns(20);
        gameRoomMapper.insert(room);

        Map<String, Object> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("message", "房间创建成功");
        return result;
    }

    /**
     * 获取房间信息
     */
    @GetMapping("/room/{roomId}")
    public Map<String, Object> getRoomInfo(@PathVariable String roomId) {
        GameRoom room = gameRoomMapper.selectOne(
                new LambdaQueryWrapper<GameRoom>().eq(GameRoom::getRoomId, roomId));

        List<RoomPlayer> players = roomPlayerMapper.selectList(
                new LambdaQueryWrapper<RoomPlayer>().eq(RoomPlayer::getRoomId, roomId));

        Map<String, Object> result = new HashMap<>();
        result.put("room", room);
        result.put("players", players);
        return result;
    }

    /**
     * 获取房间消息历史（新加入玩家拉取）
     */
    @GetMapping("/room/{roomId}/messages")
    public List<GameRecord> getRoomMessages(@PathVariable String roomId) {
        return gameRecordMapper.selectList(
                new LambdaQueryWrapper<GameRecord>()
                        .eq(GameRecord::getRoomId, roomId)
                        .orderByAsc(GameRecord::getCreateTime));
    }

    /**
     * 获取活跃房间列表
     */
    @GetMapping("/room/active")
    public List<GameRoom> getActiveRooms() {
        return gameRoomMapper.selectList(
                new LambdaQueryWrapper<GameRoom>()
                        .eq(GameRoom::getStatus, "WAITING")
                        .or()
                        .eq(GameRoom::getStatus, "PLAYING")
                        .orderByDesc(GameRoom::getCreateTime));
    }

}
