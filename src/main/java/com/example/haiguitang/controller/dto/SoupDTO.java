package com.example.haiguitang.controller.dto;


import com.example.haiguitang.entity.SoupStory;
import lombok.Data;

import java.util.List;

@Data
public class SoupDTO {

    private List<SoupStory> list;

    private int total;

    private String strategy;


    public SoupDTO(List<SoupStory> list, int total, String strategy) {
        this.list = list;
        this.total = total;
        this.strategy = strategy;
    }

    public SoupDTO() {
    }
}
