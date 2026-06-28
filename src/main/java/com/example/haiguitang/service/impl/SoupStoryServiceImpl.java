package com.example.haiguitang.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.haiguitang.entity.SoupStory;
import com.example.haiguitang.mapper.SoupStoryMapper;
import com.example.haiguitang.service.SoupStoryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class SoupStoryServiceImpl implements SoupStoryService {

    @Resource
    private SoupStoryMapper soupStoryMapper;

    /*
    * 混合推荐策略
    *  - count <= 2: 全部随机
    *  - count > 2: 2条热门 + 剩余随机，去重后打乱
    * */
    @Override
    public List<SoupStory> recommend(int count) {
        if (count <= 2) {
            return recommendRandom(count);
        }

        int hotCount = 2;
        int randomCount = count - hotCount;

        //获取热门
        List<SoupStory> hotList = recommendHot(hotCount);
        Set<Long> usedIds = new HashSet<>();
        /*for (SoupStory soupStory : hotList) {
            usedIds.add(soupStory.getId());
        }*/
        hotList.forEach(hot -> usedIds.add(hot.getId()));

        //获取随机(排除已选的热门)
        List<SoupStory> randomList = recommendRandomExclude(randomCount, usedIds);

        //合并并打乱顺序
        List<SoupStory> result = new ArrayList<>();
        result.addAll(hotList);
        result.addAll(randomList);
        Collections.shuffle(result);

        return result;
    }

    @Override
    public List<SoupStory> recommendHot(int count) {
        return soupStoryMapper.selectList(
                new LambdaQueryWrapper<SoupStory>()
                        .orderByDesc(SoupStory::getPlayCount)
                        .last("limit " + count)

        );
        // select * from soup_story order by play_count desc limit count
    }

    @Override
    public List<SoupStory> recommendRandom(int count) {
        long total = soupStoryMapper.selectCount(null);
        if (total == 0) return Collections.emptyList();

        // 如果请求数量 >= 总数, 直接返回全部并打乱
        if (count >= total) {
            List<SoupStory> all = soupStoryMapper.selectList(null);
            Collections.shuffle(all);
            return all;
        }

        // 随机选取 count 条不重复的
        return soupStoryMapper.selectList(
                new LambdaQueryWrapper<SoupStory>()
                        .last("ORDER BY RAND() LIMIT" + count));

    }

    /*
    * 随机推荐，排除指定 ID
    * */
    private List<SoupStory> recommendRandomExclude(int count, Set<Long> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) {
            return recommendRandom(count);
        }

        long total = soupStoryMapper.selectCount(null);
        long available = total - excludeIds.size();
        if (available <= 0) return Collections.emptyList();

        if (count >= available) {
            List<SoupStory> all = soupStoryMapper.selectList(
                    new LambdaQueryWrapper<SoupStory>()
                            .notIn(SoupStory::getId, excludeIds));
            Collections.shuffle(all);
            return all;
        }

        return soupStoryMapper.selectList(
                new LambdaQueryWrapper<SoupStory>()
                        .notIn(SoupStory::getId, excludeIds)
                        .last("ORDER BY RAND() LIMIT" + count));
    }

    @Override
    public SoupStory getById(Long id) {
        return soupStoryMapper.selectById(id);
    }

    @Override
    public List<SoupStory> listAll() {
        return soupStoryMapper.selectList(
                new LambdaQueryWrapper<SoupStory>()
                        .orderByDesc(SoupStory::getCreateTime)
        );
    }
}
