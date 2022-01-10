/**
 * Copyright (c) 2022 myons Inc. All rights reserved.
 */
package com.zmwh.esclient.config;

import com.zmwh.esclient.service.ElasticsearchIndexService;
import com.zmwh.esclient.service.ElasticsearchService;
import org.springframework.context.annotation.Import;

/**
 * @describe: 引入配置 、
 * @author: dmzmwh 、
 * @time: 2022-01-10 17:59 、
 */
@Import({ElasticsearchConfig.class,
        ElasticsearchService.class,
        ElasticsearchProperties.class,
        ElasticsearchIndexService.class})
public class ImportConfig {
}
