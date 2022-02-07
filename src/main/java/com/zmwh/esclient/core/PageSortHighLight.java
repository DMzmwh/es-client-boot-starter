package com.zmwh.esclient.core;

import lombok.Data;

/**
 * 
 * description: 分页+排序+高亮对象封装
 * author: dmzmwh
 * create: 2019-01-21 17:09
 **/
@Data
public class PageSortHighLight {
    private int currentPage;
    private int pageSize;
    Sort sort = new Sort();
    private HighLight highLight = new HighLight();

    public PageSortHighLight(int currentPage, int pageSize) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
    }

    public PageSortHighLight(int currentPage, int pageSize, Sort sort) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.sort = sort;
    }
}
