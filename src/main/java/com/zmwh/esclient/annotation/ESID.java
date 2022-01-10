package com.zmwh.esclient.annotation;


import com.zmwh.esclient.enums.DataType;

import java.lang.annotation.*;

/**
 * description: ES entity 标识ID的注解,在es entity field上添加
 * author: dmzmwh
 * create: 2019-01-18 16:092
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface ESID {
    /**
     * 数据类型（包含 关键字类型）
     */
    DataType datatype() default DataType.keyword_type;
}
