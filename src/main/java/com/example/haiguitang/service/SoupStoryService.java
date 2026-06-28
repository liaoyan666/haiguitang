package com.example.haiguitang.service;

import com.example.haiguitang.entity.SoupStory;

import java.util.List;

public interface SoupStoryService {

    /**
     * 混合推荐：热门 + 随机混排
     * @param count 推荐数量
     */
    List<SoupStory> recommend(int count);

    /**
     * 热门推荐
     */
    List<SoupStory> recommendHot(int count);

    /**
     * 随机推荐
     */
    List<SoupStory> recommendRandom(int count);

    /**
     * 根据ID查询
     */
    SoupStory getById(Long id);

    /**
     * 查询全部
     */
    List<SoupStory> listAll();
}