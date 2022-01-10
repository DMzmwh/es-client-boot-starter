/**
 * Copyright (c) 2022 myons Inc. All rights reserved.
 */
package com.zmwh.esclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @describe: es参数配置 、
 * @author: dmzmwh 、
 * @time: 2019-09-03 9:47 、
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "zmwh.elasticsearch")
public class ElasticsearchProperties {

    private String host = "127.0.0.1:9200";
    private String username;
    private String password;
    /**
     * 连接池里的最大连接数
     */
    private Integer maxConnectTotal = 3000;
    /**
     * 某一个/每服务每次能并行接收的请求数量
     */
    private Integer maxConnectPerRoute = 500;
    /**
     * http clilent中从connetcion pool中获得一个connection的超时时间
     */
    private Integer connectionRequestTimeoutMillis = 5000;
    /**
     * 响应超时时间，超过此时间不再读取响应
     */
    private Integer socketTimeoutMillis = 30000;
    /**
     * 链接建立的超时时间
     */
    private Integer connectTimeoutMillis = 2000;
    /**
     * 心跳检测 -1 表示长连接
     */
    private Long keepAliveStrategy = -1L;
    /**
     * 索引后后缀配置
     */
    private String suffix;
}
