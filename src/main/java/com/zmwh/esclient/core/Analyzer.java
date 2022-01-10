/**
 * Copyright (c) 2022 myons Inc. All rights reserved.
 */
package com.zmwh.esclient.core;

import lombok.Data;

/**
 * @describe: 分词类型 、
 * @author: dmzmwh 、
 * @time: 2022-01-04 10:17 、
 */
@Data
public class Analyzer {
    public static final String standard = "standard";//支持中文采用的方法为单字切分。他会将词汇单元转换成小写形式，并去除停用词和标点符号
    public static final String simple = "simple";//首先会通过非字母字符来分割文本信息，然后将词汇单元统一为小写形式。该分析器会去掉数字类型的字符
    public static final String whitespace = "whitespace";//仅仅是去除空格，对字符没有lowcase化;不支持中文
    public static final String stop = "stop";//StopAnalyzer的功能超越了SimpleAnalyzer，在SimpleAnalyzer的基础上增加了去除英文中的常用单词（如the，a等）
    public static final String keyword = "keyword";//不分词，直接将输入当作输出
    public static final String pattern = "pattern";
    public static final String fingerprint = "fingerprint";
    public static final String english = "english";//语言分词器（英文）
    public static final String ik_smart = "ik_smart";//ik中文智能分词 https://github.com/medcl/elasticsearch-analysis-ik/
    public static final String ik_max_word = "ik_max_word";//ik中文分词
}
