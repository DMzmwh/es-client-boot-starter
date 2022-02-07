package com.zmwh.esclient.utils;

import cn.hutool.core.util.ObjectUtil;
import com.zmwh.esclient.annotation.ESFieId;
import com.zmwh.esclient.annotation.ESID;
import com.zmwh.esclient.enums.DataType;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @describe:  工具类 、
 * @author: dmzmwh 、
 * @time: 2019-10-03 13:27 、
 */
public class Tools {
    /**
     * 根据对象中的注解获取ID的字段值
     * @param obj
     * @return
     */
    public static String getESId(Object obj) throws Exception {
        Field[] fields = obj.getClass().getDeclaredFields();
        for(Field f : fields){
            f.setAccessible(true);
            ESID esid = f.getAnnotation(ESID.class);
            if(esid != null){
                Object value = f.get(obj);
                if(value == null){
                    return null;
                }else{
                    return value.toString();
                }
            }
        }
        return null;
    }

    public static boolean arrayISNULL(Object[] objs){
        if(objs == null || objs.length == 0){
            return true;
        }
        boolean flag = false;
        for (int i = 0; i < objs.length; i++) {
            if(ObjectUtil.isNotEmpty(objs[i])){
                flag = true;
            }
        }
        if(flag){
            return false;
        }else{
            return true;
        }
    }


    /**
     * 判断当前类是否包含nested字段
     */
    private static Map<Class,Boolean> checkNested = new HashMap<>();

    public static boolean checkNested(List list){
        if(list == null || list.size() == 0){
            return false;
        }
        return checkNested(list.get(0));
    }
    public static boolean checkNested(Object obj){
        if(obj == null){
            return false;
        }
        if(checkNested.containsKey(obj.getClass())){
            return checkNested.get(obj.getClass());
        }else {
            for (int i = 0; i < obj.getClass().getDeclaredFields().length; i++) {
                Field f = obj.getClass().getDeclaredFields()[i];
                if (f.getAnnotation(ESFieId.class)!= null
                        && (f.getAnnotation(ESFieId.class).datatype() == DataType.nested_type
                             || (f.getAnnotation(ESFieId.class).nested_class() != null && f.getAnnotation(ESFieId.class).nested_class() != Object.class))) {
                    checkNested.put(obj.getClass(), true);
                    return true;
                }
            }
            checkNested.put(obj.getClass(), false);
            return false;
        }
    }
}
