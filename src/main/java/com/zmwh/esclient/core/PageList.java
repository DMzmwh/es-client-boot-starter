package com.zmwh.esclient.core;

import lombok.Data;

import java.util.List;

/**
 * 
 * description: 分页对象封装
 * author: dmzmwh
 * create: 2019-01-21 17:06
 **/
@Data
public class PageList<T> {
    List<T> list;

    private int totalPages = 0;

    private long totalElements = 0;

    private Object[] sortValues;

    private int currentPage;

    private int pageSize;
}
