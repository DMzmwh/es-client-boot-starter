package com.zmwh.esclient.core;

import lombok.Data;

/**
 * 
 * description: 下钻聚合分析返回对象
 * author: dmzmwh
 * create: 2019-02-19 16:06
 **/
@Data
public class Down {
    String level_1_key;
    String level_2_key;

    Object value;

    @Override
    public String toString() {
        return "Down{" +
                "level_1_key='" + level_1_key + '\'' +
                ", level_2_key='" + level_2_key + '\'' +
                ", value=" + value +
                '}';
    }
}
