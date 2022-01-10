/**
 * Copyright (c) 2022 myons Inc. All rights reserved.
 */
package com.zmwh.esclient.config;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * @describe: es配置文具盒 、
 * @author: dmzmwh 、
 * @time: 2019-09-03 10:21 、
 */
@Slf4j
@Configuration
@AllArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchProperties elasticsearchProperties;

    @Bean(destroyMethod = "close",name = "client")
    public RestHighLevelClient restHighLevelClient() {
        String host = elasticsearchProperties.getHost();
        String username = elasticsearchProperties.getUsername();
        String password = elasticsearchProperties.getPassword();
        Integer maxConnectTotal = elasticsearchProperties.getMaxConnectTotal();
        Integer maxConnectPerRoute = elasticsearchProperties.getMaxConnectPerRoute();
        Integer connectionRequestTimeoutMillis = elasticsearchProperties.getConnectionRequestTimeoutMillis();
        Integer socketTimeoutMillis = elasticsearchProperties.getSocketTimeoutMillis();
        Integer connectTimeoutMillis = elasticsearchProperties.getConnectTimeoutMillis();
        Long strategy = elasticsearchProperties.getKeepAliveStrategy();

        String[] hosts = host.split(",");
        List<HttpHost> httpHostsList = new ArrayList<>(hosts.length);
        for (String hostStr : hosts) {
            String[] hostAndPort = hostStr.split(":");
            HttpHost httpHost = new HttpHost(hostAndPort[0], Integer.parseInt(hostAndPort[1]), HttpHost.DEFAULT_SCHEME_NAME);
            httpHostsList.add(httpHost);
        }
        HttpHost[] httpHosts = httpHostsList.toArray(new HttpHost[hosts.length]);
        RestClientBuilder builder = RestClient.builder(httpHosts);
        builder.setRequestConfigCallback(f -> f.setConnectTimeout(connectTimeoutMillis).setSocketTimeout(socketTimeoutMillis).setConnectionRequestTimeout(connectionRequestTimeoutMillis));

        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder.disableAuthCaching();
            httpClientBuilder.setMaxConnTotal(maxConnectTotal);
            httpClientBuilder.setMaxConnPerRoute(maxConnectPerRoute);

            if (StrUtil.isNotBlank(username)) {//没有用户名密码可以不用这个设置
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(username, password));  //es账号密码（默认用户名为elastic）
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
            if (strategy > 0) {
                httpClientBuilder.setKeepAliveStrategy((httpResponse, httpContext) -> strategy);
            }
            return httpClientBuilder;
        });

        return new RestHighLevelClient(builder);
    }
}
