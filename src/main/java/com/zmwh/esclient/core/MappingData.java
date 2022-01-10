package com.zmwh.esclient.core;

import lombok.Data;

import java.util.List;

/**
 * @describe: 索引信息操作工具类 、
 * @author: dmzmwh 、
 * @time: 2019-09-11 11:25 、
 */
@Data
public class MappingData {

    String field_name;
    /**
     * 数据类型（包含 关键字类型）
     *
     * @return
     */
    String datatype;
    /**
     * 间接关键字
     *
     * @return
     */
    boolean keyword;
    /**
     * 是否单独的存储
     *
     * @return
     */
    boolean store;
    /**
     * 是否索引
     *
     * @return
     */
    boolean index;

    /**
     * 关键字忽略字数
     *
     * @return
     */
    int ignore_above;
    /**
     * 是否支持ngram，高效全文搜索提示
     *
     * @return
     */
    boolean ngram;
    /**
     * 是否支持suggest，高效前缀搜索提示
     *
     * @return
     */
    boolean suggest;
    /**
     * 索引分词器设置
     *
     * @return
     */
    String analyzer;
    /**
     * 搜索内容分词器设置
     *
     * @return
     */
    String search_analyzer;

    private String copy_to;

    private String null_value;

    private Class nested_class;


    /**
     * 时间格式
     */
    private List<String> dateFormat;

    /**
     * normalizer名称指定，需要配合自定义settings使用
     */
    private String normalizer;


}
