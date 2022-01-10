package com.zmwh.esclient.utils;


import cn.hutool.core.util.StrUtil;
import com.zmwh.esclient.annotation.ESFieId;
import com.zmwh.esclient.annotation.ESID;
import com.zmwh.esclient.annotation.ESSettings;
import com.zmwh.esclient.config.ElasticsearchProperties;
import com.zmwh.esclient.core.MappingData;
import com.zmwh.esclient.core.SettingsData;
import com.zmwh.esclient.enums.DataType;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @describe: 索引信息操作工具类 、
 * @author: dmzmwh 、
 * @time: 2019-10-03 08:21 、
 */
@Component
@AllArgsConstructor
public class IndexTools {

    private final ElasticsearchProperties elasticsearchProperties;

    ///**
    // * 获取索引元数据：indexname、indextype
    // * @param clazz
    // * @return
    // */
    //public static MetaData getIndexType(Class<?> clazz){
    //    String indexname = "";
    //    String indextype = "";
    //    if(clazz.getAnnotation(ESMetaData.class) != null){
    //        indexname = clazz.getAnnotation(ESMetaData.class).indexName();
    //        indextype = clazz.getAnnotation(ESMetaData.class).indexType();
    //        if(indextype == null || indextype.equals("")){indextype = "_doc";}
    //        MetaData metaData = new MetaData(indexname,indextype);
    //        metaData.setPrintLog(clazz.getAnnotation(ESMetaData.class).printLog());
    //        if(Tools.arrayISNULL(clazz.getAnnotation(ESMetaData.class).searchIndexNames())) {
    //            metaData.setSearchIndexNames(new String[]{indexname});
    //        }else{
    //            metaData.setSearchIndexNames((clazz.getAnnotation(ESMetaData.class).searchIndexNames()));
    //        }
    //        return metaData;
    //    }
    //    return null;
    //}
    //
    ///**
    // * 获取索引元数据：主分片、备份分片数的配置
    // * @param clazz
    // * @return
    // */
    //public static MetaData getShardsConfig(Class<?> clazz){
    //    int number_of_shards = 0;
    //    int number_of_replicas = 0;
    //    if(clazz.getAnnotation(ESMetaData.class) != null){
    //        number_of_shards = clazz.getAnnotation(ESMetaData.class).number_of_shards();
    //        number_of_replicas = clazz.getAnnotation(ESMetaData.class).number_of_replicas();
    //        MetaData metaData = new MetaData(number_of_shards,number_of_replicas);
    //        metaData.setPrintLog(clazz.getAnnotation(ESMetaData.class).printLog());
    //        return metaData;
    //    }
    //    return null;
    //}

    /**
     * 根据枚举类型获得mapping中的类型
     *
     * @param dataType
     * @return
     */
    private static String getType(DataType dataType) {
        return dataType.toString().replaceAll("_type", "");
    }

    /**
     * 获取索引元数据：indexname、主分片、备份分片数 等配置
     *
     * @param clazz
     * @return
     */
    public SettingsData getSettingsData(Class<?> clazz) {
        SettingsData settingsData = null;
        if (clazz.getAnnotation(ESSettings.class) != null) {
            String indexname = "";
            String indextype = "";
            int number_of_shards = 0;
            int number_of_replicas = 0;
            indexname = clazz.getAnnotation(ESSettings.class).indexName();
            indextype = clazz.getAnnotation(ESSettings.class).indexType();
            number_of_shards = clazz.getAnnotation(ESSettings.class).number_of_shards();
            number_of_replicas = clazz.getAnnotation(ESSettings.class).number_of_replicas();
            settingsData = new SettingsData(indexname, indextype, number_of_shards, number_of_replicas);
            //如果配置了Suffix则自动添加后缀到索引名称
            if (clazz.getAnnotation(ESSettings.class).suffix()) {
                settingsData.setSuffix(elasticsearchProperties.getSuffix());
                if (settingsData.getSuffix() != null && !"".equals(settingsData.getSuffix())) {
                    settingsData.setIndexname(settingsData.getIndexname() + "_" + settingsData.getSuffix());
                    indexname = settingsData.getIndexname();
                }
            }
            settingsData.setPrintLog(clazz.getAnnotation(ESSettings.class).printLog());
            String[] strings = clazz.getAnnotation(ESSettings.class).searchIndexNames();
            if (Tools.arrayISNULL(clazz.getAnnotation(ESSettings.class).searchIndexNames())) {
                settingsData.setSearchIndexNames(new String[]{indexname});
            } else {
                //如果配置了searchIndexNames，则以配置为准
                settingsData.setSearchIndexNames((clazz.getAnnotation(ESSettings.class).searchIndexNames()));
            }
            settingsData.setAlias(clazz.getAnnotation(ESSettings.class).alias());
            settingsData.setAliasIndex(clazz.getAnnotation(ESSettings.class).aliasIndex());
            settingsData.setWriteIndex(clazz.getAnnotation(ESSettings.class).writeIndex());
            settingsData.setRollover(clazz.getAnnotation(ESSettings.class).rollover());
            settingsData.setRolloverMaxIndexAgeCondition(clazz.getAnnotation(ESSettings.class).rolloverMaxIndexAgeCondition());
            settingsData.setRolloverMaxIndexAgeTimeUnit(clazz.getAnnotation(ESSettings.class).rolloverMaxIndexAgeTimeUnit());
            settingsData.setRolloverMaxIndexDocsCondition(clazz.getAnnotation(ESSettings.class).rolloverMaxIndexDocsCondition());
            settingsData.setRolloverMaxIndexSizeCondition(clazz.getAnnotation(ESSettings.class).rolloverMaxIndexSizeCondition());
            settingsData.setRolloverMaxIndexSizeByteSizeUnit(clazz.getAnnotation(ESSettings.class).rolloverMaxIndexSizeByteSizeUnit());
            settingsData.setMaxResultWindow(clazz.getAnnotation(ESSettings.class).maxResultWindow());
            settingsData.setAutoRollover(clazz.getAnnotation(ESSettings.class).autoRollover());
            settingsData.setAutoCreateIndex(clazz.getAnnotation(ESSettings.class).autoCreateIndex());
            if(StrUtil.isBlank(clazz.getAnnotation(ESSettings.class).settingsPath())){
                settingsData.setSettingsPath(settingsData.getIndexname()+".essettings");
            }else{
                settingsData.setSettingsPath(clazz.getAnnotation(ESSettings.class).settingsPath());
            }
            return settingsData;
        } else {
            throw new IllegalArgumentException("未配置@ESSettings注解");
        }
    }

    /**
     * 获取配置于Field上的mapping信息，如果未配置注解，则给出默认信息
     *
     * @param field
     * @return
     */
    public MappingData getMappingData(Field field) {
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        MappingData mappingData = new MappingData();
        mappingData.setField_name(field.getName());
        if (field.getAnnotation(ESFieId.class) != null) {
            ESFieId esMapping = field.getAnnotation(ESFieId.class);
            mappingData.setDatatype(getType(esMapping.datatype()));
            mappingData.setAnalyzer(esMapping.analyzer());
            if (StrUtil.isNotBlank(esMapping.custom_analyzer())){
                mappingData.setAnalyzer(esMapping.custom_analyzer());
            }
            mappingData.setSearch_analyzer(esMapping.search_analyzer());
            if(StrUtil.isNotBlank(esMapping.custom_search_analyzer())){
                mappingData.setSearch_analyzer(esMapping.custom_search_analyzer());
            }
            mappingData.setNgram(esMapping.ngram());
            mappingData.setIgnore_above(esMapping.ignore_above());
            if (mappingData.getDatatype().equals("text")) {
                mappingData.setKeyword(esMapping.keyword());
            } else {
                mappingData.setKeyword(false);
            }
            mappingData.setSuggest(esMapping.suggest());
            mappingData.setStore(esMapping.store());
            mappingData.setIndex(esMapping.index());
            mappingData.setCopy_to(esMapping.copy_to());
            mappingData.setNested_class(esMapping.nested_class());
            if (StrUtil.isNotBlank(esMapping.null_value())) {
                mappingData.setNull_value(esMapping.null_value());
            }
            //add date format
            if (DataType.date_type.equals(esMapping.datatype())) {
                String[] formats = esMapping.dateFormat();
                if (formats != null) {
                    List<String> list = new CopyOnWriteArrayList<>(formats);
                    list.forEach(s -> {
                        if (StrUtil.isBlank(s)) list.remove(s);
                    });
                    if (!CollectionUtils.isEmpty(list)) mappingData.setDateFormat(list);
                }
            }
            mappingData.setNormalizer(esMapping.normalizer());
        } else {
            mappingData.setKeyword(false);
            if (field.getAnnotation(ESID.class) != null) {
                ESID esid = field.getAnnotation(ESID.class);
                mappingData.setIndex(true);
                mappingData.setDatatype(getType(esid.datatype()));
//                mappingData.setDatatype(getType(DataType.keyword_type));
            } else {
                if (field.getType() == String.class) {
                    mappingData.setDatatype(getType(DataType.text_type));
                    mappingData.setKeyword(true);
                } else if (field.getType() == Short.class || field.getType() == short.class) {
                    mappingData.setDatatype(getType(DataType.short_type));
                } else if (field.getType() == Integer.class || field.getType() == int.class) {
                    mappingData.setDatatype(getType(DataType.integer_type));
                } else if (field.getType() == Long.class || field.getType() == long.class) {
                    mappingData.setDatatype(getType(DataType.long_type));
                } else if (field.getType() == Float.class || field.getType() == float.class) {
                    mappingData.setDatatype(getType(DataType.float_type));
                } else if (field.getType() == Double.class || field.getType() == double.class) {
                    mappingData.setDatatype(getType(DataType.double_type));
                } else if (field.getType() == BigDecimal.class) {
                    mappingData.setDatatype(getType(DataType.double_type));
                } else if (field.getType() == Boolean.class || field.getType() == boolean.class) {
                    mappingData.setDatatype(getType(DataType.boolean_type));
                } else if (field.getType() == Byte.class || field.getType() == byte.class) {
                    mappingData.setDatatype(getType(DataType.byte_type));
                } else if (field.getType() == Date.class) {
                    mappingData.setDatatype(getType(DataType.date_type));
                } else {
                    mappingData.setDatatype(getType(DataType.text_type));
                    mappingData.setKeyword(true);
                }
            }
            mappingData.setAnalyzer("standard");
            mappingData.setNgram(false);
            mappingData.setIgnore_above(256);
            mappingData.setSearch_analyzer("standard");
            mappingData.setSuggest(false);
            mappingData.setIndex(true);
            mappingData.setCopy_to("");
            mappingData.setNested_class(null);
        }
        return mappingData;
    }

    /**
     * 批量获取配置于Field上的mapping信息，如果未配置注解，则给出默认信息
     *
     * @param clazz
     * @return
     */
    public MappingData[] getMappingData(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        MappingData[] mappingDataList = new MappingData[fields.length];
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getName().equals("serialVersionUID")) {
                continue;
            }
            mappingDataList[i] = getMappingData(fields[i]);
        }
        return mappingDataList;
    }
}
