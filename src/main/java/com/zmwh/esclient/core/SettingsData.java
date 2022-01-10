/**
 * Copyright (c) 2022 myons Inc. All rights reserved.
 */
package com.zmwh.esclient.core;

import lombok.Data;
import org.elasticsearch.common.unit.ByteSizeUnit;

import java.util.concurrent.TimeUnit;

/**
 * @describe: es 索引设置 、
 * @author: dmzmwh 、
 * @time: 2022-01-03 13:28 、
 */
@Data
public class SettingsData {


    private String indexname = "";
    @Deprecated
    private String indextype = "";
    private String[] searchIndexNames;
    private int number_of_shards;
    private int number_of_replicas;
    private boolean printLog = false;
    private boolean alias;
    private String[] aliasIndex;
    private String writeIndex;
    private boolean rollover;
    private long rolloverMaxIndexAgeCondition;
    private TimeUnit rolloverMaxIndexAgeTimeUnit;
    private long rolloverMaxIndexDocsCondition;
    private long rolloverMaxIndexSizeCondition;
    private ByteSizeUnit rolloverMaxIndexSizeByteSizeUnit;
    private boolean autoRollover;
    private long autoRolloverInitialDelay;
    private long autoRolloverPeriod;
    private TimeUnit  autoRolloverTimeUnit;
    //indexName的后缀，一般用于配置中环境的区分
    private String suffix;
    private boolean autoCreateIndex;
    private long maxResultWindow;
    private String settingsPath;
    /** 是否支持ngram，高效全文搜索提示 */
    boolean ngram;

    public SettingsData(String indexname, String indextype) {
        this.indexname = indexname;
        this.indextype = indextype;
    }

    public SettingsData(String indexname, String indextype, int number_of_shards, int number_of_replicas) {
        this.indexname = indexname;
        this.indextype = indextype;
        this.number_of_shards = number_of_shards;
        this.number_of_replicas = number_of_replicas;
    }

    public SettingsData(int number_of_shards, int number_of_replicas) {
        this.number_of_shards = number_of_shards;
        this.number_of_replicas = number_of_replicas;
    }
}
