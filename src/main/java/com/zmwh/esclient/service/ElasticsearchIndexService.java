package com.zmwh.esclient.service;

import cn.hutool.core.util.StrUtil;
import com.zmwh.esclient.core.MappingData;
import com.zmwh.esclient.core.SettingsData;
import com.zmwh.esclient.enums.DataType;
import com.zmwh.esclient.exception.RRException;
import com.zmwh.esclient.utils.IndexTools;
import com.zmwh.esclient.utils.Tools;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @describe: 索引结构基础方法实现类 、
 * @author: dmzmwh 、
 * @time: 2019-09-03 10:21 、
 */
@Slf4j
@Component
@AllArgsConstructor
public class ElasticsearchIndexService<T> {

    private final RestHighLevelClient client;
    private static final String NESTED = "nested";
    private final IndexTools indexTools;


    /**
     * 创建索引
     * @param clazz
     * @throws Exception
     */
    public void createIndex(Class<T> clazz) throws Exception{
        SettingsData settingsData = indexTools.getSettingsData(clazz);
        XContentBuilder xContentBuilder = buildIndexMapping(clazz);
        Settings.Builder builder = buildIndexSettings(settingsData);
        CreateIndexRequest request = null;
        if(settingsData.isRollover()){//如果配置了rollover则替换索引名称为rollover名称，并创建对应的alias
            if(settingsData.getRolloverMaxIndexAgeCondition() == 0
                    && settingsData.getRolloverMaxIndexDocsCondition() == 0
                    && settingsData.getRolloverMaxIndexSizeCondition() == 0) {
                throw new RuntimeException("rolloverMaxIndexAgeCondition is zero OR rolloverMaxIndexDocsCondition is zero OR rolloverMaxIndexSizeCondition is zero");
            }
            request = new CreateIndexRequest("<"+settingsData.getIndexname()+"-{now/d}-000001>");
            Alias alias = new Alias(settingsData.getIndexname());
            alias.writeIndex(true);
            request.alias(alias);
        }else{
            request = new CreateIndexRequest(settingsData.getIndexname());
        }
        try {
            request.settings(builder);
            request.mapping(xContentBuilder);
            CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
            //返回的CreateIndexResponse允许检索有关执行的操作的信息，如下所示：
            boolean acknowledged = createIndexResponse.isAcknowledged();//指示是否所有节点都已确认请求
            log.info("创建索引["+settingsData.getIndexname()+"]结果："+acknowledged);
        } catch (IOException e) {
            log.error("createIndex error",e);
        }
    }

    /**
     * 构建基础的 properties
     * @return
     * @throws IOException
     */
    public XContentBuilder buildIndexMappingProperties(MappingData[] mappingDataList, XContentBuilder mappingBuilder) throws IOException {
        mappingBuilder.startObject("properties");
        for (MappingData mappingData : mappingDataList) {
            if(mappingData == null || mappingData.getField_name() == null){
                continue;
            }
            mappingBuilder.startObject(mappingData.getField_name())
                    .field("type", mappingData.getDatatype());
            if(StrUtil.isNotBlank(mappingData.getNormalizer())) {
                mappingBuilder.field("normalizer", mappingData.getNormalizer());
            }

            //add date format //TODO
            if(DataType.date_type.toString().replaceAll("_type","").equals(mappingData.getDatatype()) && !CollectionUtils.isEmpty(mappingData.getDateFormat())){
                String format = String.join(" || ",mappingData.getDateFormat());
                mappingBuilder.field("format", format);
            }
            //嵌套
            if (mappingData.getDatatype().equals(NESTED)){
                if(mappingData.getNested_class() != null && mappingData.getNested_class() != Object.class){
                    MappingData[] submappingDataList = indexTools.getMappingData(mappingData.getNested_class());
                    mappingBuilder = buildIndexMappingProperties(submappingDataList,mappingBuilder);
                }else {
                    throw new RRException("请设置嵌套索引的 nested_class 例子：@ESMapping(datatype = DataType.nested_type,nested_class = Product.class)");
                }
            }else {
                if(StrUtil.isNotBlank(mappingData.getCopy_to())) {
                    mappingBuilder.field("copy_to", mappingData.getCopy_to());
                }
                if(StrUtil.isNotBlank(mappingData.getNull_value())) {
                    mappingBuilder.field("null_value", mappingData.getNull_value());
                }
                mappingBuilder.field("index", mappingData.isIndex());
                if (mappingData.isStore()){
                    mappingBuilder.field("store", true);
                }
                if (mappingData.isNgram() && (mappingData.getDatatype().equals("text") || mappingData.getDatatype().equals("keyword"))) {
                    mappingBuilder.field("analyzer", "autocomplete");
                    mappingBuilder.field("search_analyzer", "standard");

                } else if (mappingData.getDatatype().equals("text")) {
                    mappingBuilder.field("analyzer", mappingData.getAnalyzer());
                    mappingBuilder.field("search_analyzer", mappingData.getSearch_analyzer());
                }

                if (mappingData.isKeyword() && !mappingData.getDatatype().equals("keyword") && mappingData.isSuggest()) {
                    mappingBuilder.startObject("fields");

                    mappingBuilder.startObject("keyword");
                    mappingBuilder.field("type", "keyword");
                    mappingBuilder.field("ignore_above", mappingData.getIgnore_above());
                    mappingBuilder.endObject();

                    mappingBuilder.startObject("suggest");
                    mappingBuilder.field("type", "completion");
                    mappingBuilder.field("analyzer", mappingData.getAnalyzer());
                    mappingBuilder.endObject();

                    mappingBuilder.endObject();
                } else if (mappingData.isKeyword() && !mappingData.getDatatype().equals("keyword") && !mappingData.isSuggest()) {
                    mappingBuilder.startObject("fields");

                    mappingBuilder.startObject("keyword");
                    mappingBuilder.field("type", "keyword");
                    mappingBuilder.field("ignore_above", mappingData.getIgnore_above());
                    mappingBuilder.endObject();

                    mappingBuilder.endObject();

                } else if (!mappingData.isKeyword() && mappingData.isSuggest()) {
                    mappingBuilder.startObject("fields");

                    mappingBuilder.startObject("suggest");
                    mappingBuilder.field("type", "completion");
                    mappingBuilder.field("analyzer", mappingData.getAnalyzer());
                    mappingBuilder.endObject();

                    mappingBuilder.endObject();
                }
            }
            mappingBuilder.endObject();
        }

        return mappingBuilder.endObject();
    }

    /**
     * 生成索引mapping
     * @param clazz
     * @return
     * @throws Exception
     */
    public XContentBuilder buildIndexMapping(Class clazz) throws Exception {
        //此处构建 mappings 内容

        XContentBuilder mappingBuilder = JsonXContent.contentBuilder()
                .startObject();
        MappingData[] mappingDataList = indexTools.getMappingData(clazz);
        mappingBuilder = buildIndexMappingProperties(mappingDataList,mappingBuilder);

        mappingBuilder.endObject();
        return mappingBuilder;
    }


    /**
     * 生成索引mapping
     * @param settingsData
     * @return
     * @throws Exception
     */
    public Settings.Builder buildIndexSettings(SettingsData settingsData) throws Exception {
        Settings.Builder builder = null;
        if (settingsData.isNgram()){
            builder = Settings.builder()
                    .put("index.number_of_shards", settingsData.getNumber_of_shards())
                    .put("index.number_of_replicas", settingsData.getNumber_of_replicas())
                    .put("index.max_result_window", settingsData.getMaxResultWindow())
                    .put("analysis.filter.autocomplete_filter.type","edge_ngram")
                    .put("analysis.filter.autocomplete_filter.min_gram",1)
                    .put("analysis.filter.autocomplete_filter.max_gram",20)
                    .put("analysis.analyzer.autocomplete.type","custom")
                    .put("analysis.analyzer.autocomplete.tokenizer","standard")
                    .putList("analysis.analyzer.autocomplete.filter",new String[]{"lowercase","autocomplete_filter"});
        }else{
            builder = Settings.builder()
                    .put("index.number_of_shards", settingsData.getNumber_of_shards())
                    .put("index.number_of_replicas", settingsData.getNumber_of_replicas())
                    .put("index.max_result_window", settingsData.getMaxResultWindow());
        }
        ClassPathResource classPathResource = new ClassPathResource(settingsData.getSettingsPath());
        if(classPathResource.exists()){
            List<String> settings = new BufferedReader(new InputStreamReader(classPathResource.getInputStream()))
                    .lines().collect(Collectors.toList());
            Map<String,String> map = resoveSettings(settings);
            for(String key:map.keySet()){
                builder.put(key,map.get(key));
            }
        }
        return builder;
    }

    /**
     * 解析settings内容
     * @param settings
     * @return
     */
    private Map<String,String> resoveSettings(List<String> settings){
        Map map = new HashMap();
        if(settings != null && settings.size() > 0){
            settings.forEach(s -> {
                String[] split = s.split(":");
                map.put(split[0],split[1]);
            });
        }
        return map;
    }


    public void switchAliasWriteIndex(Class<T> clazz, String writeIndex) throws Exception {
        SettingsData settingsData = indexTools.getSettingsData(clazz);
        if(settingsData.isAlias()){//当配置了别名后自动创建索引功能将失效
            if(Tools.arrayISNULL(settingsData.getAliasIndex())){
                throw new RuntimeException("aliasIndex must not be null");
            }
            if(StrUtil.isBlank(writeIndex)){
                //如果WriteIndex为空则默认为最后一个AliasIndex为WriteIndex
                settingsData.setWriteIndex(settingsData.getAliasIndex()[settingsData.getAliasIndex().length-1]);
            }else if(!Stream.of(settingsData.getAliasIndex()).collect(Collectors.toList()).contains(settingsData.getWriteIndex())){
                throw new RuntimeException("aliasIndex must contains writeIndex");
            }
            //创建Alias
            IndicesAliasesRequest request = new IndicesAliasesRequest();
            Stream.of(settingsData.getAliasIndex()).forEach(s -> {
                IndicesAliasesRequest.AliasActions aliasAction =
                        new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
                                .index(s)
                                .alias(settingsData.getIndexname());
                if(s.equals(writeIndex)){
                    aliasAction.writeIndex(true);
                }
                request.addAliasAction(aliasAction);
            });
            AcknowledgedResponse indicesAliasesResponse = client.indices().updateAliases(request, RequestOptions.DEFAULT);
            log.info("更新Alias["+settingsData.getIndexname()+"]结果："+indicesAliasesResponse.isAcknowledged());
        }
    }


    public void createAlias(Class<T> clazz) throws Exception {
        SettingsData settingsData = indexTools.getSettingsData(clazz);
        if(settingsData.isAlias()){//当配置了别名后自动创建索引功能将失效
            if(Tools.arrayISNULL(settingsData.getAliasIndex())){
                throw new RuntimeException("aliasIndex must not be null");
            }
            if(StrUtil.isBlank(settingsData.getWriteIndex())){
                //如果WriteIndex为空则默认为最后一个AliasIndex为WriteIndex
                settingsData.setWriteIndex(settingsData.getAliasIndex()[settingsData.getAliasIndex().length-1]);
            }else if(!Stream.of(settingsData.getAliasIndex()).collect(Collectors.toList()).contains(settingsData.getWriteIndex())){
                throw new RuntimeException("aliasIndex must contains writeIndex");
            }
            //判断Alias是否存在，如果存在则直接跳出
            GetAliasesRequest requestWithAlias = new GetAliasesRequest(settingsData.getIndexname());
            boolean exists = client.indices().existsAlias(requestWithAlias, RequestOptions.DEFAULT);
            if(exists){
                log.info("Alias["+settingsData.getIndexname()+"]已经存在");
            }else{
                //创建Alias
                IndicesAliasesRequest request = new IndicesAliasesRequest();
                Stream.of(settingsData.getAliasIndex()).forEach(s -> {
                    IndicesAliasesRequest.AliasActions aliasAction =
                            new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
                                    .index(s)
                                    .alias(settingsData.getIndexname());
                    if(s.equals(settingsData.getWriteIndex())){
                        aliasAction.writeIndex(true);
                    }
                    request.addAliasAction(aliasAction);
                });
                AcknowledgedResponse indicesAliasesResponse = client.indices().updateAliases(request, RequestOptions.DEFAULT);
                log.info("创建Alias["+settingsData.getIndexname()+"]结果："+indicesAliasesResponse.isAcknowledged());
            }
        }
    }


    public void createIndex(Map<String, String> settings,Map<String, String[]> settingsList, String mappingJson,String indexName) throws Exception {
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        Settings.Builder build = Settings.builder();
        if(settings != null){
            settings.forEach((k,v) ->build.put(k,v));
        }
        if(settingsList != null){
            settings.forEach((k,v) ->build.putList(k,v));
        }
        request.mapping(mappingJson,XContentType.JSON);
        try {
            CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
            //返回的CreateIndexResponse允许检索有关执行的操作的信息，如下所示：
            boolean acknowledged = createIndexResponse.isAcknowledged();//指示是否所有节点都已确认请求
            log.info("创建索引["+indexName+"]结果："+acknowledged);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public void dropIndex(Class<T> clazz) throws Exception {
        SettingsData settingsData = indexTools.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        DeleteIndexRequest request = new DeleteIndexRequest(indexname);
        client.indices().delete(request, RequestOptions.DEFAULT);
    }


    public boolean exists(Class<T> clazz) throws Exception{
        SettingsData settingsData = indexTools.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        GetIndexRequest request = new GetIndexRequest(indexname);
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        return exists;
    }


    public void rollover(Class<T> clazz,boolean isAsyn) throws Exception {
        if(clazz == null)return;
        SettingsData settingsData = indexTools.getSettingsData(clazz);
        if(!settingsData.isRollover())return;
        if(settingsData.isAutoRollover()){
            rollover(settingsData);
            return;
        }else {
            if (isAsyn) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1024);//歇一会，等1s插入后生效
                        rollover(settingsData);
                    } catch (Exception e) {
                        log.error("rollover error",e);
                    }
                }).start();
            } else {
                rollover(settingsData);
            }
        }
    }


    public String getIndexName(Class<T> clazz) {
        return getSettingsData(clazz).getIndexname();
    }


    public SettingsData getSettingsData(Class<T> clazz) {
        return indexTools.getSettingsData(clazz);
    }


    public MappingData[] getMappingData(Class<T> clazz) {
        return indexTools.getMappingData(clazz);
    }


    private void rollover(SettingsData settingsData) throws Exception {
        RolloverRequest request = new RolloverRequest(settingsData.getIndexname(),null);
        if(settingsData.getRolloverMaxIndexAgeCondition() != 0){
            request.addMaxIndexAgeCondition(new TimeValue(settingsData.getRolloverMaxIndexAgeCondition(), settingsData.getRolloverMaxIndexAgeTimeUnit()));
        }
        if(settingsData.getRolloverMaxIndexDocsCondition() != 0){
            request.addMaxIndexDocsCondition(settingsData.getRolloverMaxIndexDocsCondition());
        }
        if(settingsData.getRolloverMaxIndexSizeCondition() != 0){
            request.addMaxIndexSizeCondition(new ByteSizeValue(settingsData.getRolloverMaxIndexSizeCondition(), settingsData.getRolloverMaxIndexSizeByteSizeUnit()));
        }
        RolloverResponse rolloverResponse = client.indices().rollover(request, RequestOptions.DEFAULT);
        log.info("rollover alias["+settingsData.getIndexname()+"]结果：" + rolloverResponse.isAcknowledged());
    }
}
