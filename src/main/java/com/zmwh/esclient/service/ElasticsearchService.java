package com.zmwh.esclient.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zmwh.esclient.annotation.ESFieId;
import com.zmwh.esclient.annotation.ESID;
import com.zmwh.esclient.config.ElasticsearchProperties;
import com.zmwh.esclient.constants.Constants;
import com.zmwh.esclient.core.*;
import com.zmwh.esclient.core.response.ScrollResponse;
import com.zmwh.esclient.core.response.SqlResponse;
import com.zmwh.esclient.core.response.UriResponse;
import com.zmwh.esclient.enums.AggsType;
import com.zmwh.esclient.enums.DataType;
import com.zmwh.esclient.enums.SqlFormat;
import com.zmwh.esclient.utils.BeanTools;
import com.zmwh.esclient.utils.Constant;
import com.zmwh.esclient.utils.HttpClientTool;
import com.zmwh.esclient.utils.Tools;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.*;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.*;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.script.mustache.SearchTemplateRequest;
import org.elasticsearch.script.mustache.SearchTemplateResponse;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filters;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.elasticsearch.search.suggest.phrase.DirectCandidateGeneratorBuilder;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestion;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestionBuilder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @describe: Elasticsearch??????????????????????????? ???
 * @author: dmzmwh ???
 * @time: 2019-09-03 16:03 ???
 */
@Slf4j
@Component
@AllArgsConstructor
public class ElasticsearchService<T, M> {

    private final RestHighLevelClient client;
    private final ElasticsearchIndexService elasticsearchIndex;


    
    public Response request(Request request) throws Exception {
        Response response = client.getLowLevelClient().performRequest(request);
        return response;
    }

    
    public boolean save(T t) throws Exception {
        return save(t,null);
    }


    public boolean save(T t, String routing) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        String id = Tools.getESId(t);
        IndexRequest indexRequest = new IndexRequest(indexname);
        if (StrUtil.isNotBlank(id)) {
            indexRequest = indexRequest.id(id);
        }
        String source = JSONUtil.toJsonStr(t);
        indexRequest.source(source, XContentType.JSON);
        if (StrUtil.isNotBlank(routing)){
            indexRequest.routing(routing);
        }
        IndexResponse indexResponse = null;
        indexResponse = client.index(indexRequest, RequestOptions.DEFAULT);
        if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
            log.info("INDEX CREATE SUCCESS");
            //????????????rollover
            elasticsearchIndex.rollover(t.getClass(),true);
        } else if (indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
            log.info("INDEX UPDATE SUCCESS");
        } else {
            return false;
        }
        return true;
    }

    
    public BulkResponse save(List<T> list) throws Exception {
        if (CollUtil.isEmpty(list)) {
            return null;
        }
        T t = list.get(0);
        SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        return savePart(list,indexname);
    }

    public BulkResponse[] saveBatch(List<T> list) throws Exception {
        return this.saveBatch(list,null);
    }
    
    public BulkResponse[] saveBatch(List<T> list,Integer batchSize) throws Exception {
        if (CollUtil.isEmpty(list)) {
            return null;
        }
        if (batchSize == null){
            batchSize = Constants.BULK_COUNT;
        }
        T t = list.get(0);
        SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        List<List<T>> lists = CollUtil.split(list, batchSize);
        BulkResponse[] bulkResponses = new BulkResponse[lists.size()];
        for (int i = 0; i < lists.size(); i++) {
            bulkResponses[i] = savePart(lists.get(i),indexname);
        }
        return bulkResponses;
    }

    private BulkResponse savePart(List<T> list,String indexname) throws Exception {
        BulkRequest rrr = new BulkRequest();
        Class clazz = null;
        for (int i = 0; i < list.size(); i++) {
            T tt = list.get(i);
            clazz = tt.getClass();
            String id = Tools.getESId(tt);
            String sourceJsonStr = JSONUtil.toJsonStr(tt);
            IndexRequest indexRequest = new IndexRequest(indexname);
            indexRequest = indexRequest.id(id);
            rrr.add(indexRequest
//                    .source(BeanTools.objectToMap(tt)));
                    .source(sourceJsonStr, XContentType.JSON));
        }
        BulkResponse bulkResponse = client.bulk(rrr, RequestOptions.DEFAULT);
        //????????????rollover
        elasticsearchIndex.rollover(clazz,true);
        return bulkResponse;
    }

    
    public BulkResponse bulkUpdate(List<T> list) throws Exception {
        if (CollUtil.isEmpty(list)){
            return null;
        }
        T t = list.get(0);
        if(Tools.checkNested(t)){
            throw new Exception("nested????????????????????????????????????");
        }
        SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        return updatePart(list, indexname);
    }

    
    public BulkResponse[] bulkUpdateBatch(List<T> list) throws Exception {
        return this.bulkUpdateBatch(list,null);
    }



    public BulkResponse[] bulkUpdateBatch(List<T> list,Integer bulkCount) throws Exception {
        if(CollUtil.isEmpty(list)){
            return null;
        }
        if (bulkCount == null){
            bulkCount = Constants.BULK_COUNT;
        }
        T t = list.get(0);
        if(Tools.checkNested(t)){
            throw new Exception("nested????????????????????????????????????");
        }
        SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        List<List<T>> lists = CollUtil.split(list, bulkCount);
        BulkResponse[] bulkResponses = new BulkResponse[lists.size()];
        for (int i = 0; i < lists.size(); i++) {
            bulkResponses[i] = updatePart(lists.get(i),indexname);
        }
        return bulkResponses;
    }

    private BulkResponse updatePart(List<T> list,String indexname) throws Exception {
        BulkRequest rrr = new BulkRequest();
        for (T item : list) {
            String id = Tools.getESId(item);
            Map<String, Object> map = BeanUtil.beanToMap(item);
            rrr.add(new UpdateRequest(indexname, id)
                    .doc(map));
        }
        BulkResponse bulkResponse = client.bulk(rrr, RequestOptions.DEFAULT);
        return bulkResponse;
    }

    
    public boolean update(T t) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        String id = Tools.getESId(t);
        if (StrUtil.isBlank(id)) {
            throw new Exception("ID cannot be empty");
        }
        if(Tools.checkNested(t)){
            throw new Exception("nested????????????????????????????????????");
        }
        UpdateRequest updateRequest = new UpdateRequest(indexname, id);
        Map<String, Object> map = BeanUtil.beanToMap(t);
        updateRequest.doc(map);
        UpdateResponse updateResponse = null;
        updateResponse = client.update(updateRequest, RequestOptions.DEFAULT);
        if (updateResponse.getResult() == DocWriteResponse.Result.CREATED) {
            log.info("INDEX CREATE SUCCESS");
        } else if (updateResponse.getResult() == DocWriteResponse.Result.UPDATED) {
            log.info("INDEX UPDATE SUCCESS");
        } else {
            return false;
        }
        return true;
    }


    public BulkByScrollResponse updateByQuery(QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        UpdateByQueryRequest request = new UpdateByQueryRequest(indexname);
        request.setQuery(queryBuilder);
        BulkByScrollResponse bulkResponse = client.updateByQuery(request, RequestOptions.DEFAULT);
        return bulkResponse;
    }

    public BulkResponse batchUpdate(QueryBuilder queryBuilder, T t, Class clazz, int limitcount, boolean asyn) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        if (queryBuilder == null) {
            throw new NullPointerException();
        }
        if(Tools.checkNested(t)){
            throw new Exception("nested????????????????????????????????????");
        }
        if(Tools.getESId(t) == null || "".equals(Tools.getESId(t))) {
            PageSortHighLight psh = new PageSortHighLight(1, limitcount);
            psh.setHighLight(null);
            PageList pageList = this.search(queryBuilder, psh, clazz, indexname);
            if (pageList.getTotalElements() > limitcount) {
                throw new Exception("beyond the limitcount");
            }
            if (asyn) {
                new Thread(() -> {
                    try {
                        batchUpdate(pageList.getList(), indexname, t);
                        log.info("asyn batch finished update");
                    } catch (Exception e) {
                        log.error("asyn batch update fail", e);
                    }
                }).start();
                return null;
            } else {
                return batchUpdate(pageList.getList(), indexname, t);
            }
        }else{
            throw new Exception("????????????????????????????????????");
        }
    }

    private BulkResponse batchUpdate(List<T> list, String indexname,T tot) throws Exception {
        Map<String, Object> map = BeanUtil.beanToMap(tot);
        BulkRequest rrr = new BulkRequest();
        for (T t : list) {
            rrr.add(new UpdateRequest(indexname, Tools.getESId(t))
                    .doc(map));
        }
        BulkResponse bulkResponse = client.bulk(rrr, RequestOptions.DEFAULT);
        return bulkResponse;
    }


    
    public boolean updateCover(T t) throws Exception {
        return save(t);
    }

    
    public boolean delete(T t) throws Exception {
        return delete(t,null);
    }

    
    public boolean delete(T t, String routing) throws Exception {
              SettingsData settingsData = elasticsearchIndex.getSettingsData(t.getClass());
        String indexname = settingsData.getIndexname();
        String id = Tools.getESId(t);
        if (StrUtil.isBlank(id)) {
            throw new Exception("ID cannot be empty");
        }
        DeleteRequest deleteRequest = new DeleteRequest(indexname, id);
        if(StrUtil.isNotBlank(routing)){
            deleteRequest.routing(routing);
        }
        DeleteResponse deleteResponse = null;
        deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT);
        if (deleteResponse.getResult() == DocWriteResponse.Result.DELETED) {
            log.info("INDEX DELETE SUCCESS");
        } else {
            return false;
        }
        return true;
    }

    
    public BulkByScrollResponse deleteByQuery(QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        DeleteByQueryRequest request = new DeleteByQueryRequest(indexname);
        request.setQuery(queryBuilder);
        BulkByScrollResponse bulkResponse = client.deleteByQuery(request, RequestOptions.DEFAULT);
        return bulkResponse;
    }


    
    public SearchResponse search(SearchRequest searchRequest) throws IOException {
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        return searchResponse;
    }

    
    public List<T> search(QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return search(queryBuilder, clazz, indexname);
    }

    
    public List<T> search(QueryBuilder queryBuilder, Class<T> clazz, String... indexs) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        List<T> list = new ArrayList<>();
        SearchRequest searchRequest = new SearchRequest(indexs);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(Constant.DEFALT_PAGE_SIZE);
        searchRequest.source(searchSourceBuilder);
        if (settingsData.isPrintLog()) {
            log.info(searchSourceBuilder.toString());
        }
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHits hits = searchResponse.getHits();
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit hit : searchHits) {
            T t = JSONUtil.toBean(hit.getSourceAsString(), clazz);
            //???_id?????????????????????@ESID???????????????
            correctID(clazz, t, (M)hit.getId());
            list.add(t);
        }
        return list;
    }

    
    public List<T> searchMore(QueryBuilder queryBuilder,int limitSize, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return searchMore(queryBuilder,limitSize,clazz,indexname);
    }

    
    public List<T> searchMore(QueryBuilder queryBuilder,int limitSize, Class<T> clazz, String... indexs) throws Exception {
        PageSortHighLight pageSortHighLight = new PageSortHighLight(1, limitSize);
        PageList pageList = search(queryBuilder, pageSortHighLight, clazz, indexs);
        if(pageList != null){
            return pageList.getList();
        }
        return null;
    }

    
    public List<T> searchUri(String uri, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        List<T> list = new ArrayList<>();
        Request request = new Request("GET","/"+indexname+"/_doc/_search/?"+uri);
        Response response = request(request);
        String responseBody = EntityUtils.toString(response.getEntity());
        if (settingsData.isPrintLog()) {
            log.info("searchUri???????????????"+"/"+indexname+"/_doc/_search/?"+uri);
            log.info("searchUri???????????????"+responseBody);
        }
        UriResponse uriResponse = JSONUtil.toBean(responseBody, UriResponse.class);
        T[] ts = (T[]) Array.newInstance(clazz, uriResponse.getHits().getHits().size());
        for (int i = 0; i < uriResponse.getHits().getHits().size(); i++) {
            T t = (T) clazz.newInstance();
            //??????LinkedHashMap???json????????????Map??????????????????Object
            Object obj = BeanTools.mapToObject((Map) uriResponse.getHits().getHits().get(i).get_source(),clazz);
            //???Object????????????
            BeanUtils.copyProperties(obj, t);
            //???_id?????????????????????@ESID???????????????
            correctID(clazz, t, (M)uriResponse.getHits().getHits().get(i).get_id());
            ts[i] = t;
        }
        return Arrays.asList(ts);
    }

    private final ElasticsearchProperties elasticsearchProperties;


    /**
     * modules ?????? xpack-sql
     * https://www.elastic.co/guide/en/elasticsearch/reference/7.6/xpack-sql.html
     * @param sql
     * @param sqlFormat
     * @return
     * @throws Exception
     */
    public String queryBySQL(String sql, SqlFormat sqlFormat) throws Exception {
        String host = elasticsearchProperties.getHost();
        String ipport = "";
        String[] hosts = host.split(",");
        if(hosts.length == 1){
            ipport = hosts[0];
        }else{//???????????????????????????
            int randomindex = new Random().nextInt(hosts.length);
            ipport = hosts[randomindex];
        }
        ipport = "http://"+ipport;
        log.info(ipport+"/_sql?format="+sqlFormat.getFormat());
        log.info("{\"query\":\""+sql+"\"}");

        String username = elasticsearchProperties.getUsername();
        String password = elasticsearchProperties.getPassword();
        if(StrUtil.isNotBlank(username)) {
            return HttpClientTool.execute(ipport+"/_sql?format="+sqlFormat.getFormat(),"{\"query\":\""+sql+"\"}",username,password);
        }
        return HttpClientTool.execute(ipport+"/_sql?format="+sqlFormat.getFormat(),"{\"query\":\""+sql+"\"}");
    }


    /**
     * ??????sql????????????
     * @throws Exception
     */
    public List<T> queryBySQL(String sql, Class<T> clazz) throws Exception {
        String s = queryBySQL(sql, SqlFormat.JSON);
        SqlResponse sqlResponse = JSONUtil.toBean(s, SqlResponse.class);
        List<T> result = new ArrayList<>();
        if(sqlResponse != null && !CollectionUtils.isEmpty(sqlResponse.getRows())){
            for (List<String> row : sqlResponse.getRows()) {
                result.add(generateObjBySQLReps(sqlResponse.getColumns(),row,clazz));
            }
        }
        return result;
    }

    private <T> T generateObjBySQLReps(List<SqlResponse.ColumnsDTO> columns,List<String> rows,Class<T> clazz) throws Exception {
        if(rows.size() != columns.size()){
            throw new Exception("sql column not match");
        }
        Map<String, BeanTools.NameTypeValueMap> valueMap = new HashMap();
        for (int i = 0; i < rows.size(); i++) {
            BeanTools.NameTypeValueMap m = new BeanTools.NameTypeValueMap();
            m.setDataType(DataType.getDataTypeByStr(columns.get(i).getType()));
            m.setFieldName(columns.get(i).getName());
            m.setValue(rows.get(i));
            valueMap.put(columns.get(i).getName(),m);
        }
        T t = (T)BeanTools.typeMapToObject(valueMap, clazz);
        return t;
    }



    /**
     * ????????????
     *
     * @throws Exception
     */
    public long count(QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return count(queryBuilder, clazz, indexname);
    }

    /**
     * ????????????
     *
     * @throws Exception
     */
    public long count(QueryBuilder queryBuilder, Class<T> clazz, String... indexs) throws Exception {
        CountRequest countRequest = new CountRequest(indexs);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        countRequest.source(searchSourceBuilder);
        CountResponse countResponse = client.count(countRequest, RequestOptions.DEFAULT);
        long count = countResponse.getCount();
        return count;
    }

    
    public T getById(M id, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        if (ObjectUtil.isEmpty(id)){
            throw new Exception("ID ????????????");
        }
        GetRequest getRequest = new GetRequest(indexname, id.toString());
        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
        if (getResponse.isExists()) {
            return JSONUtil.toBean(getResponse.getSourceAsString(), clazz);
        }
        return null;
    }

    
    public List<T> mgetById(M[] ids, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        MultiGetRequest request = new MultiGetRequest();
        for (int i = 0; i < ids.length; i++) {
            request.add(new MultiGetRequest.Item(indexname, ids[i].toString()));
        }
        MultiGetResponse response = client.mget(request, RequestOptions.DEFAULT);
        List<T> list = new ArrayList<>();
        for (int i = 0; i < response.getResponses().length; i++) {
            MultiGetItemResponse item = response.getResponses()[i];
            GetResponse getResponse = item.getResponse();
            if (getResponse.isExists()) {
                list.add(JSONUtil.toBean(getResponse.getSourceAsString(), clazz));
            }
        }
        return list;
    }

    
    public boolean exists(M id, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        if (ObjectUtil.isEmpty(id)){
            throw new Exception("ID ????????????");
        }
        GetRequest getRequest = new GetRequest(indexname, id.toString());
        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
        if (getResponse.isExists()) {
            return true;
        }
        return false;
    }


    
    public Map aggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String bucketName) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return aggs(metricName, aggsType, queryBuilder, clazz, bucketName, indexname);
    }

    
    public Map aggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String bucketName, String... indexs) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = indexs;
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        Field f_bucket = clazz.getDeclaredField(bucketName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        if (f_bucket == null) {
            throw new Exception("bucket field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        bucketName = genKeyword(f_bucket, bucketName);

        //????????????????????????????????????keyword
        String by = "by_" + bucketName.replaceAll(keyword, "");
        String me = aggsType.toString() + "_" + metricName.replaceAll(keyword, "");

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        TermsAggregationBuilder aggregation = AggregationBuilders.terms(by)
                .field(bucketName);
        //????????????????????????????????????
        aggregation.order(BucketOrder.aggregation(me, false));
//        aggregation.order(BucketOrder.key(false));
        if (AggsType.count == aggsType) {
            aggregation.subAggregation(AggregationBuilders.count(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.min == aggsType) {
            aggregation.subAggregation(AggregationBuilders.min(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.max == aggsType) {
            aggregation.subAggregation(AggregationBuilders.max(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.sum == aggsType) {
            aggregation.subAggregation(AggregationBuilders.sum(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.avg == aggsType) {
            aggregation.subAggregation(AggregationBuilders.avg(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        }
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(aggregation);


        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        if (settingsData.isPrintLog()) {
            log.info(searchSourceBuilder.toString());
        }
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        Aggregations aggregations = searchResponse.getAggregations();
        Terms by_risk_code = aggregations.get(by);
        Map map = new LinkedHashMap();
        for (Terms.Bucket bucket : by_risk_code.getBuckets()) {
            if (AggsType.count == aggsType) {
                ValueCount count = bucket.getAggregations().get(me);
                long value = count.getValue();
                map.put(bucket.getKey(), value);
            } else if (AggsType.min == aggsType) {
                ParsedMin min = bucket.getAggregations().get(me);
                double value = min.getValue();
                map.put(bucket.getKey(), value);
            } else if (AggsType.max == aggsType) {
                ParsedMax max = bucket.getAggregations().get(me);
                double value = max.getValue();
                map.put(bucket.getKey(), value);
            } else if (AggsType.sum == aggsType) {
                ParsedSum sum = bucket.getAggregations().get(me);
                double value = sum.getValue();
                map.put(bucket.getKey(), value);
            } else if (AggsType.avg == aggsType) {
                ParsedAvg avg = bucket.getAggregations().get(me);
                double value = avg.getValue();
                map.put(bucket.getKey(), value);
            }
        }
        return map;
    }

    
    public List<Down> aggswith2level(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String[] bucketNames) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return aggswith2level(metricName, aggsType, queryBuilder, clazz, bucketNames, indexname);
    }

    
    public List<Down> aggswith2level(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String[] bucketNames, String... indexs) throws Exception {
        String[] indexname = indexs;
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        if (bucketNames == null) {
            throw new NullPointerException();
        }
        if (bucketNames.length != 2) {
            throw new Exception("???????????????????????????!");
        }
        Field[] f_buckets = new Field[bucketNames.length];
        for (int i = 0; i < bucketNames.length; i++) {
            f_buckets[i] = clazz.getDeclaredField(bucketNames[i].replaceAll(keyword, ""));
            if (f_buckets[i] == null) {
                throw new Exception("bucket field is null");
            }
        }
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        String me = aggsType.toString() + "_" + metricName.replaceAll(keyword, "");

        String[] bys = new String[bucketNames.length];
        for (int i = 0; i < f_buckets.length; i++) {
            bucketNames[i] = genKeyword(f_buckets[i], bucketNames[i]);
            bys[i] = "by_" + bucketNames[i].replaceAll(keyword, "");
        }
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        TermsAggregationBuilder[] termsAggregationBuilders = new TermsAggregationBuilder[bucketNames.length];
        for (int i = 0; i < bucketNames.length; i++) {
            TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(bys[i]).field(bucketNames[i]);
            termsAggregationBuilders[i] = aggregationBuilder;
        }
        for (int i = 0; i < termsAggregationBuilders.length; i++) {
            if (i != termsAggregationBuilders.length - 1) {
                termsAggregationBuilders[i].subAggregation(termsAggregationBuilders[i + 1]).size(Constant.AGG_RESULT_COUNT);
            }
        }
        if (AggsType.count == aggsType) {
            termsAggregationBuilders[termsAggregationBuilders.length - 1]
                    .subAggregation(AggregationBuilders.count(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.min == aggsType) {
            termsAggregationBuilders[termsAggregationBuilders.length - 1]
                    .subAggregation(AggregationBuilders.min(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.max == aggsType) {
            termsAggregationBuilders[termsAggregationBuilders.length - 1]
                    .subAggregation(AggregationBuilders.max(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.sum == aggsType) {
            termsAggregationBuilders[termsAggregationBuilders.length - 1]
                    .subAggregation(AggregationBuilders.sum(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        } else if (AggsType.avg == aggsType) {
            termsAggregationBuilders[termsAggregationBuilders.length - 1]
                    .subAggregation(AggregationBuilders.avg(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        }
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(termsAggregationBuilders[0]);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        //???????????????2??????????????????
        List<Down> downList = new ArrayList<>();
        Terms terms1 = searchResponse.getAggregations().get(bys[0]);
        Terms terms2;
        for (Terms.Bucket bucket : terms1.getBuckets()) {
            terms2 = bucket.getAggregations().get(bys[1]);
            for (Terms.Bucket bucket2 : terms2.getBuckets()) {
                Down down = new Down();
                down.setLevel_1_key(bucket.getKey().toString());
                down.setLevel_2_key(bucket2.getKey().toString());
                if (AggsType.count == aggsType) {
                    ValueCount count = bucket2.getAggregations().get(me);
                    long value = count.getValue();
                    down.setValue(value);
                } else if (AggsType.min == aggsType) {
                    ParsedMin min = bucket2.getAggregations().get(me);
                    double value = min.getValue();
                    down.setValue(value);
                } else if (AggsType.max == aggsType) {
                    ParsedMax max = bucket2.getAggregations().get(me);
                    double value = max.getValue();
                    down.setValue(value);
                } else if (AggsType.sum == aggsType) {
                    ParsedSum sum = bucket2.getAggregations().get(me);
                    double value = sum.getValue();
                    down.setValue(value);
                } else if (AggsType.avg == aggsType) {
                    ParsedAvg avg = bucket2.getAggregations().get(me);
                    double value = avg.getValue();
                    down.setValue(value);
                }
                downList.add(down);
            }
        }
        return downList;
    }


    private static final String keyword = ".keyword";

    /**
     * ????????????????????????.keyword???????????????es?????????????????????field_data???
     *  1?????????????????????????????????.keyword?????????
     *  2??????????????????????????????????????????text????????????
     *  3????????????????????????????????????text?????????????????????ESMapping?????????kerword???????????????????????????ESMapping????????????keyword?????????
     *  4????????????????????????????????????text?????????????????????ESMapping????????????kerword???????????????????????????
     *  doc values
     *      ???????????????????????????????????????????????????????????????
     *      ?????????????????? ??????????????????????????????
     *      doc values?????????????????????????????????????????????????????????os????????????????????????????????????????????????????????????????????????????????????os????????????????????????
     *      ??????field????????????doc values??????????????????????????????
     * field_data
     *      ????????????????????????????????????????????????????????????????????????field_data????????????????????????
     *      Fielddata???doc values??????????????????
     *      ??????????????????
     *      ?????????heap?????????
     * ?????????????????????????????????????????????????????????field_data??????ESMapping??????keyword??????
     * @param field
     * @param name
     * @return
     */
    private String genKeyword(Field field, String name) {
        ESFieId esMapping = field.getAnnotation(ESFieId.class);
        //??????.keyword????????????
        if (name == null || name.indexOf(keyword) > -1) {
            return name;
        }
        //??????keyword???true????????????
        //????????????????????????????????????????????????keyword???true
        if (esMapping == null) {
            if (field.getType() == String.class) {
                return name + keyword;
            }
        }
        //????????????????????????????????????????????????keyword???true
        else {
            if (esMapping.datatype() == DataType.text_type && esMapping.keyword() == true) {
                return name + keyword;
            }
        }
        return name;
    }

    
    public double aggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return aggs(metricName, aggsType, queryBuilder, clazz, indexname);
    }

    
    public double aggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String... indexs) throws Exception {
        String[] indexname = indexs;
        String me = aggsType.toString() + "_" + metricName.replaceAll(keyword, "");
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        if (AggsType.count == aggsType) {
            searchSourceBuilder.aggregation(AggregationBuilders.count(me).field(metricName));
        } else if (AggsType.min == aggsType) {
            searchSourceBuilder.aggregation(AggregationBuilders.min(me).field(metricName));
        } else if (AggsType.max == aggsType) {
            searchSourceBuilder.aggregation(AggregationBuilders.max(me).field(metricName));
        } else if (AggsType.sum == aggsType) {
            searchSourceBuilder.aggregation(AggregationBuilders.sum(me).field(metricName));
        } else if (AggsType.avg == aggsType) {
            searchSourceBuilder.aggregation(AggregationBuilders.avg(me).field(metricName));
        }
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        if (AggsType.count == aggsType) {
            ValueCount count = searchResponse.getAggregations().get(me);
            long value = count.getValue();
            return Double.parseDouble(String.valueOf(value));
        } else if (AggsType.min == aggsType) {
            ParsedMin min = searchResponse.getAggregations().get(me);
            double value = min.getValue();
            return value;
        } else if (AggsType.max == aggsType) {
            ParsedMax max = searchResponse.getAggregations().get(me);
            double value = max.getValue();
            return value;
        } else if (AggsType.sum == aggsType) {
            ParsedSum sum = searchResponse.getAggregations().get(me);
            double value = sum.getValue();
            return value;
        } else if (AggsType.avg == aggsType) {
            ParsedAvg avg = searchResponse.getAggregations().get(me);
            double value = avg.getValue();
            return value;
        }
        return 0d;
    }

    
    public Stats statsAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return statsAggs(metricName, queryBuilder, clazz, indexname);
    }

    
    public Stats statsAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz, String... indexs) throws Exception {
        String[] indexname = indexs;
        String me = "stats";
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        StatsAggregationBuilder aggregation = AggregationBuilders.stats(me).field(metricName);
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Stats stats = searchResponse.getAggregations().get(me);
        return stats;
    }

    
    public Map<String, Stats> statsAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz, String bucketName) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return statsAggs(metricName, queryBuilder, clazz, bucketName, indexname);
    }

    
    public Map<String, Stats> statsAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz, String bucketName, String... indexs) throws Exception {
        String[] indexname = indexs;
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        Field f_bucket = clazz.getDeclaredField(bucketName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        if (f_bucket == null) {
            throw new Exception("bucket field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        bucketName = genKeyword(f_bucket, bucketName);

        String by = "by_" + bucketName.replaceAll(keyword, "");
        String me = "stats" + "_" + metricName.replaceAll(keyword, "");

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        TermsAggregationBuilder aggregation = AggregationBuilders.terms(by)
                .field(bucketName);
        //????????????count???????????????
        aggregation.order(BucketOrder.count(false));
        aggregation.subAggregation(AggregationBuilders.stats(me).field(metricName)).size(Constant.AGG_RESULT_COUNT);
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        Aggregations aggregations = searchResponse.getAggregations();
        Terms by_risk_code = aggregations.get(by);
        Map<String, Stats> map = new LinkedHashMap<>();
        for (Terms.Bucket bucket : by_risk_code.getBuckets()) {
            Stats stats = bucket.getAggregations().get(me);
            map.put(bucket.getKey().toString(), stats);
        }
        return map;
    }

    
    public Aggregations aggs(AggregationBuilder aggregationBuilder, QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return aggs(aggregationBuilder, queryBuilder, clazz, indexname);
    }

    
    public Aggregations aggs(AggregationBuilder aggregationBuilder, QueryBuilder queryBuilder, Class<T> clazz, String... indexs) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = indexs;
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(aggregationBuilder);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        if (settingsData.isPrintLog()) {
            log.info(searchSourceBuilder.toString());
        }
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        return searchResponse.getAggregations();
    }

    
    public long cardinality(String metricName, QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return cardinality(metricName, queryBuilder, Constant.DEFAULT_PRECISION_THRESHOLD , clazz , indexname);
    }

    
    public long cardinality(String metricName, QueryBuilder queryBuilder, long precisionThreshold, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return cardinality(metricName, queryBuilder,precisionThreshold, clazz, indexname);
    }

    
    public long cardinality(String metricName, QueryBuilder queryBuilder, Class<T> clazz, String... indexs) throws Exception {
        return cardinality(metricName, queryBuilder, Constant.DEFAULT_PRECISION_THRESHOLD , clazz , indexs);
    }

    
    public long cardinality(String metricName, QueryBuilder queryBuilder, long precisionThreshold, Class<T> clazz, String... indexs) throws Exception {
        String[] indexname = indexs;
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        String me = "cardinality_" + metricName.replaceAll(keyword, "");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        CardinalityAggregationBuilder aggregation = AggregationBuilders
                .cardinality(me)
                .field(metricName)
                .precisionThreshold(precisionThreshold)
                ;
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Cardinality agg = searchResponse.getAggregations().get(me);
        return agg.getValue();
    }

    
    public Map<Double, Double> percentilesAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return percentilesAggs(metricName, queryBuilder, clazz, Constant.DEFAULT_PERCSEGMENT, indexname);
    }

    
    public Map percentilesAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz, double[] customSegment, String... indexs) throws Exception {
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        if (customSegment == null) {
            throw new Exception("customSegment is null");
        } else if (customSegment.length == 0) {
            throw new Exception("customSegment is null");
        }
        metricName = genKeyword(f_metric, metricName);
        String me = "percentiles_" + metricName.replaceAll(keyword, "");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        PercentilesAggregationBuilder aggregation = AggregationBuilders.percentiles(me).field(metricName).percentiles(customSegment);
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexs);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Map<Double, Double> map = new LinkedHashMap<>();
        Percentiles agg = searchResponse.getAggregations().get(me);
        for (Percentile entry : agg) {
            double percent = entry.getPercent();
            double value = entry.getValue();
            map.put(percent, value);
        }
        return map;
    }

    
    public Map percentileRanksAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz, double... customSegment) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return percentileRanksAggs(metricName, queryBuilder, clazz, customSegment, indexname);
    }

    
    public Map percentileRanksAggs(String metricName, QueryBuilder queryBuilder, Class<T> clazz, double[] customSegment, String... indexs) throws Exception {
        String[] indexname = indexs;
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        if (customSegment == null || customSegment.length == 0) {
            throw new Exception("customSegment is null");
        }
        metricName = genKeyword(f_metric, metricName);
        String me = "percentiles_" + metricName.replaceAll(keyword, "");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        PercentileRanksAggregationBuilder aggregation = AggregationBuilders.percentileRanks(me, customSegment).field(metricName);
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Map<Double, Double> map = new LinkedHashMap<>();
        PercentileRanks agg = searchResponse.getAggregations().get(me);
        for (Percentile entry : agg) {
            double percent = entry.getPercent();
            double value = entry.getValue();
            map.put(percent, value);
        }
        return map;
    }


    
    public Map filterAggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, FiltersAggregator.KeyedFilter... filters) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return filterAggs(metricName, aggsType, queryBuilder, clazz, filters, indexname);
    }

    
    public Map filterAggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, FiltersAggregator.KeyedFilter[] filters, String... indexs) throws Exception {
        String[] indexname = indexs;
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        if (filters == null) {
            throw new NullPointerException();
        }
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        String me = aggsType.toString() + "_" + metricName.replaceAll(keyword, "");
        AggregationBuilder aggregation = AggregationBuilders.filters("filteragg", filters);
        searchSourceBuilder.size(0);
        if (AggsType.count == aggsType) {
            aggregation.subAggregation(AggregationBuilders.count(me).field(metricName));
        } else if (AggsType.min == aggsType) {
            aggregation.subAggregation(AggregationBuilders.min(me).field(metricName));
        } else if (AggsType.max == aggsType) {
            aggregation.subAggregation(AggregationBuilders.max(me).field(metricName));
        } else if (AggsType.sum == aggsType) {
            aggregation.subAggregation(AggregationBuilders.sum(me).field(metricName));
        } else if (AggsType.avg == aggsType) {
            aggregation.subAggregation(AggregationBuilders.avg(me).field(metricName));
        }
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Filters agg = searchResponse.getAggregations().get("filteragg");
        Map map = new LinkedHashMap();
        for (Filters.Bucket entry : agg.getBuckets()) {
            if (AggsType.count == aggsType) {
                ValueCount count = entry.getAggregations().get(me);
                long value = count.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.min == aggsType) {
                ParsedMin min = entry.getAggregations().get(me);
                double value = min.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.max == aggsType) {
                ParsedMax max = entry.getAggregations().get(me);
                double value = max.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.sum == aggsType) {
                ParsedSum sum = entry.getAggregations().get(me);
                double value = sum.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.avg == aggsType) {
                ParsedAvg avg = entry.getAggregations().get(me);
                double value = avg.getValue();
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }

    
    public Map histogramAggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String bucketName, double interval) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return histogramAggs(metricName, aggsType, queryBuilder, clazz, bucketName, interval, indexname);
    }

    
    public Map histogramAggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String bucketName, double interval, String... indexs) throws Exception {
        String[] indexname = indexs;
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        Field f_bucket = clazz.getDeclaredField(bucketName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        if (f_bucket == null) {
            throw new Exception("bucket field is null");
        }
        metricName = genKeyword(f_metric, metricName);
        bucketName = genKeyword(f_bucket, bucketName);
        String by = "by_" + bucketName.replaceAll(keyword, "");
        String me = aggsType.toString() + "_" + metricName.replaceAll(keyword, "");

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        AggregationBuilder aggregation = AggregationBuilders.histogram(by).field(bucketName).interval(interval);
        searchSourceBuilder.size(0);
        if (AggsType.count == aggsType) {
            aggregation.subAggregation(AggregationBuilders.count(me).field(metricName));
        } else if (AggsType.min == aggsType) {
            aggregation.subAggregation(AggregationBuilders.min(me).field(metricName));
        } else if (AggsType.max == aggsType) {
            aggregation.subAggregation(AggregationBuilders.max(me).field(metricName));
        } else if (AggsType.sum == aggsType) {
            aggregation.subAggregation(AggregationBuilders.sum(me).field(metricName));
        } else if (AggsType.avg == aggsType) {
            aggregation.subAggregation(AggregationBuilders.avg(me).field(metricName));
        }
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        ParsedHistogram agg = searchResponse.getAggregations().get(by);
        Map map = new LinkedHashMap();
        for (Histogram.Bucket entry : agg.getBuckets()) {
            if (AggsType.count == aggsType) {
                ValueCount count = entry.getAggregations().get(me);
                long value = count.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.min == aggsType) {
                ParsedMin min = entry.getAggregations().get(me);
                double value = min.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.max == aggsType) {
                ParsedMax max = entry.getAggregations().get(me);
                double value = max.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.sum == aggsType) {
                ParsedSum sum = entry.getAggregations().get(me);
                double value = sum.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.avg == aggsType) {
                ParsedAvg avg = entry.getAggregations().get(me);
                double value = avg.getValue();
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }

    
    public Map dateHistogramAggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String bucketName, DateHistogramInterval interval) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return dateHistogramAggs(metricName, aggsType, queryBuilder, clazz, bucketName, interval, indexname);
    }

    
    public Map dateHistogramAggs(String metricName, AggsType aggsType, QueryBuilder queryBuilder, Class<T> clazz, String bucketName, DateHistogramInterval interval, String... indexs) throws Exception {
        String[] indexname = indexs;
        Field f_metric = clazz.getDeclaredField(metricName.replaceAll(keyword, ""));
        Field f_bucket = clazz.getDeclaredField(bucketName.replaceAll(keyword, ""));
        if (f_metric == null) {
            throw new Exception("metric field is null");
        }
        if (f_bucket == null) {
            throw new Exception("bucket field is null");
        } else if (f_bucket.getType() != Date.class) {
            throw new Exception("bucket type is not support");
        }
        ESFieId esMapping = f_bucket.getAnnotation(ESFieId.class);
        if (esMapping != null && esMapping.datatype() != DataType.date_type) {
            throw new Exception("bucket type is not support");
        }
        metricName = genKeyword(f_metric, metricName);
        bucketName = genKeyword(f_bucket, bucketName);
        String by = "by_" + bucketName.replaceAll(keyword, "");
        String me = aggsType.toString() + "_" + metricName.replaceAll(keyword, "");

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        AggregationBuilder aggregation = AggregationBuilders.dateHistogram(by).field(bucketName).dateHistogramInterval(interval);
        searchSourceBuilder.size(0);
        if (AggsType.count == aggsType) {
            aggregation.subAggregation(AggregationBuilders.count(me).field(metricName));
        } else if (AggsType.min == aggsType) {
            aggregation.subAggregation(AggregationBuilders.min(me).field(metricName));
        } else if (AggsType.max == aggsType) {
            aggregation.subAggregation(AggregationBuilders.max(me).field(metricName));
        } else if (AggsType.sum == aggsType) {
            aggregation.subAggregation(AggregationBuilders.sum(me).field(metricName));
        } else if (AggsType.avg == aggsType) {
            aggregation.subAggregation(AggregationBuilders.avg(me).field(metricName));
        }
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }
        searchSourceBuilder.aggregation(aggregation);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        ParsedDateHistogram agg = searchResponse.getAggregations().get(by);
        Map map = new LinkedHashMap();
        for (Histogram.Bucket entry : agg.getBuckets()) {
            if (AggsType.count == aggsType) {
                ValueCount count = entry.getAggregations().get(me);
                long value = count.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.min == aggsType) {
                ParsedMin min = entry.getAggregations().get(me);
                double value = min.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.max == aggsType) {
                ParsedMax max = entry.getAggregations().get(me);
                double value = max.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.sum == aggsType) {
                ParsedSum sum = entry.getAggregations().get(me);
                double value = sum.getValue();
                map.put(entry.getKey(), value);
            } else if (AggsType.avg == aggsType) {
                ParsedAvg avg = entry.getAggregations().get(me);
                double value = avg.getValue();
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }

    
    public boolean deleteById(M id, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        if (ObjectUtil.isEmpty(id)){
            throw new Exception("ID ????????????");
        }
        DeleteRequest deleteRequest = new DeleteRequest(indexname, id.toString());
        DeleteResponse deleteResponse = null;
        deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT);
        if (deleteResponse.getResult() == DocWriteResponse.Result.DELETED) {
            log.info("INDEX DELETE SUCCESS");
        } else {
            return false;
        }
        return true;
    }

    
    public PageList<T> search(QueryBuilder queryBuilder, PageSortHighLight pageSortHighLight, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        if (pageSortHighLight == null) {
            throw new NullPointerException("PageSortHighLight????????????!");
        }
        return search(queryBuilder, pageSortHighLight, clazz, indexname);
    }

    
    public PageList<T> search(QueryBuilder queryBuilder, PageSortHighLight pageSortHighLight, Class<T> clazz, String... indexs) throws Exception {
        if (pageSortHighLight == null) {
            throw new NullPointerException("PageSortHighLight????????????!");
        }
        Attach attach = new Attach();
        attach.setPageSortHighLight(pageSortHighLight);
        return search(queryBuilder,attach,clazz,indexs);
    }

    
    public PageList<T> search(QueryBuilder queryBuilder, Attach attach, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        if (attach == null) {
            throw new NullPointerException("Attach????????????!");
        }
        return search(queryBuilder, attach, clazz, indexname);
    }

    
    public PageList<T> search(QueryBuilder queryBuilder, Attach attach, Class<T> clazz, String... indexs) throws Exception {
        if (attach == null) {
            throw new NullPointerException("Attach????????????!");
        }
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        PageList<T> pageList = new PageList<>();
        List<T> list = new ArrayList<>();
        PageSortHighLight pageSortHighLight = attach.getPageSortHighLight();
        SearchRequest searchRequest = new SearchRequest(indexs);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        boolean highLightFlag = false;
        boolean idSortFlag= false;
        if(pageSortHighLight != null) {
            //??????????????????
            pageList.setCurrentPage(pageSortHighLight.getCurrentPage());
            pageList.setPageSize(pageSortHighLight.getPageSize());
            //??????
            if (pageSortHighLight.getPageSize() != 0) {
                //search after????????????from
                if(!attach.isSearchAfter()) {
                    searchSourceBuilder.from((pageSortHighLight.getCurrentPage() - 1) * pageSortHighLight.getPageSize());
                }
                searchSourceBuilder.size(pageSortHighLight.getPageSize());
            }
            //??????
            if (pageSortHighLight.getSort() != null) {
                Sort sort = pageSortHighLight.getSort();
                List<Sort.Order> orders = sort.listOrders();
                for (int i = 0; i < orders.size(); i++) {
                    if(orders.get(i).getProperty().equals("_id")){
                        idSortFlag = true;
                    }
                    searchSourceBuilder.sort(new FieldSortBuilder(orders.get(i).getProperty()).order(orders.get(i).getDirection()));
                }
            }
            //??????
            //https://www.elastic.co/guide/en/elasticsearch/reference/7.12/highlighting.html
            //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/7.12/java-rest-high-search.html#java-rest-high-search-request-highlighting
            HighLight highLight = pageSortHighLight.getHighLight();
            if(highLight != null && highLight.getHighlightBuilder() != null){
                searchSourceBuilder.highlighter(highLight.getHighlightBuilder());
            }
            else if (highLight != null && highLight.getHighLightList() != null && highLight.getHighLightList().size() != 0) {
                HighlightBuilder highlightBuilder = new HighlightBuilder();
                if (StrUtil.isNotBlank(highLight.getPreTag()) && StrUtil.isNotBlank(highLight.getPostTag())) {
                    highlightBuilder.preTags(highLight.getPreTag());
                    highlightBuilder.postTags(highLight.getPostTag());
                }
                for (int i = 0; i < highLight.getHighLightList().size(); i++) {
                    highLightFlag = true;
                    // You can set fragment_size to 0 to never split any sentence.
                    //??????????????????????????????
                    highlightBuilder.field(highLight.getHighLightList().get(i), 0);
                }
                searchSourceBuilder.highlighter(highlightBuilder);
            }
        }
        //??????searchAfter
        if(attach.isSearchAfter()){
            if(pageSortHighLight == null || pageSortHighLight.getPageSize() == 0){
                searchSourceBuilder.size(10);
            }else{
                searchSourceBuilder.size(pageSortHighLight.getPageSize());
            }
            if(attach.getSortValues() != null && attach.getSortValues().length != 0) {
                searchSourceBuilder.searchAfter(attach.getSortValues());
            }
            //????????????_id?????????????????????????????????????????????
            if(!idSortFlag){
                Sort.Order order = new Sort.Order(SortOrder.ASC,"_id");
                pageSortHighLight.getSort().and(new Sort(order));
                searchSourceBuilder.sort(new FieldSortBuilder("_id").order(SortOrder.ASC));
            }
        }
        //TrackTotalHits?????????true???????????????????????????10000?????????
        if(attach.isTrackTotalHits()){
            searchSourceBuilder.trackTotalHits(attach.isTrackTotalHits());
        }

        //????????????source
        if(attach.getExcludes()!= null || attach.getIncludes() != null){
            searchSourceBuilder.fetchSource(attach.getIncludes(),attach.getExcludes());
        }
        searchRequest.source(searchSourceBuilder);
        //??????routing
        if (StrUtil.isNotBlank(attach.getRouting())) {
            searchRequest.routing(attach.getRouting());
        }
        if (settingsData.isPrintLog()) {
            log.info(searchSourceBuilder.toString());
        }
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHits hits = searchResponse.getHits();
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit hit : searchHits) {
            T t = JSONUtil.toBean(hit.getSourceAsString(), clazz);
            //???_id?????????????????????@ESID???????????????
            correctID(clazz, t, (M)hit.getId());
            //??????????????????
            if (highLightFlag) {
                Map<String, HighlightField> hmap = hit.getHighlightFields();
                hmap.forEach((k, v) ->
                        {
                            try {
                                Object obj = mapToObject(hmap, clazz);
                                BeanUtils.copyProperties(obj, t, BeanTools.getNoValuePropertyNames(obj));
                            } catch (Exception e) {
                                log.error("convert object error", e);
                            }
                        }
                );
            }
            list.add(t);
            //????????????SearchAfter??????searchAfter
            pageList.setSortValues(hit.getSortValues());
        }

        pageList.setList(list);
        pageList.setTotalElements(hits.getTotalHits().value);
        if(pageSortHighLight != null && pageSortHighLight.getPageSize() != 0) {
            pageList.setTotalPages(getTotalPages(hits.getTotalHits().value, pageSortHighLight.getPageSize()));
        }
        return pageList;
    }

    private static Map<Class,String> classIDMap = new ConcurrentHashMap();

    /**
     * ???_id?????????????????????@ESID???????????????
     * @param clazz
     * @param t
     * @param _id
     */
    private void correctID(Class clazz, T t, M _id){
        try{
            if (ObjectUtil.isEmpty(_id)){
                return;
            }
            if(classIDMap.containsKey(clazz)){
                Field field = clazz.getDeclaredField(classIDMap.get(clazz));
                field.setAccessible(true);
                //??????????????????String????????????????????????????????????id??????id??????????????????String?????????
                if(field.get(t) == null) {
                    field.set(t, _id);
                }
                return;
            }
            for (int i = 0; i < clazz.getDeclaredFields().length; i++) {
                Field field = clazz.getDeclaredFields()[i];
                field.setAccessible(true);
                if(field.getAnnotation(ESID.class) != null){
                    classIDMap.put(clazz,field.getName());
                    //??????????????????String????????????????????????????????????id??????id??????????????????String?????????
                    if(field.get(t) == null) {
                        field.set(t, _id);
                    }
                }
            }
        }catch (Exception e){
            log.error("correctID error!",e);
        }
    }

    private Object mapToObject(Map map, Class<?> beanClass) throws Exception {
        if (map == null) {
            return null;
        }
        Object obj = beanClass.newInstance();
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (map.get(field.getName()) != null) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) {
                    continue;
                }
                field.setAccessible(true);
                if (map.get(field.getName()) instanceof HighlightField && ((HighlightField) map.get(field.getName())).fragments().length > 0) {
                    field.set(obj, ((HighlightField) map.get(field.getName())).fragments()[0].string());
                }
            }
        }
        return obj;
    }

    
    public List<T> searchTemplate(Map<String, Object> template_params, String templateName, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        SearchTemplateRequest request = new SearchTemplateRequest();
        request.setRequest(new SearchRequest(indexname));

        request.setScriptType(ScriptType.STORED);
        request.setScript(templateName);
        Map<String, Object> params = new HashMap<>();
        if(template_params != null){
            template_params.forEach((k,v) -> {
                params.put(k, v);
            });
        }
        request.setScriptParams(params);
        SearchTemplateResponse response = client.searchTemplate(request, RequestOptions.DEFAULT);
        SearchResponse searchResponse = response.getResponse();
        SearchHits hits = searchResponse.getHits();
        SearchHit[] searchHits = hits.getHits();
        List<T> list = new ArrayList<>();
        for (SearchHit hit : searchHits) {
            T t = JSONUtil.toBean(hit.getSourceAsString(), clazz);
            list.add(t);
        }
        return list;
    }

    
    public List<T> searchTemplateBySource(Map<String, Object> template_params, String templateSource, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        SearchTemplateRequest request = new SearchTemplateRequest();
        request.setRequest(new SearchRequest(indexname));
        request.setScriptType(ScriptType.INLINE);
        request.setScript(templateSource);
        Map<String, Object> scriptParams = new HashMap<>();
        if(template_params != null){
            template_params.forEach((k,v) -> {
                scriptParams.put(k, v);
            });
        }
        request.setScriptParams(scriptParams);
        SearchTemplateResponse response = client.searchTemplate(request, RequestOptions.DEFAULT);
        SearchResponse searchResponse = response.getResponse();
        SearchHits hits = searchResponse.getHits();
        SearchHit[] searchHits = hits.getHits();
        List<T> list = new ArrayList<>();
        for (SearchHit hit : searchHits) {
            T t = JSONUtil.toBean(hit.getSourceAsString(), clazz);
            list.add(t);
        }
        return list;
    }

    
    public Response saveTemplate(String templateName, String templateSource) throws Exception {
        Request scriptRequest = new Request("POST", "_scripts/"+templateName);
        scriptRequest.setJsonEntity(templateSource);
        Response scriptResponse = request(scriptRequest);
        return scriptResponse;
    }

    
    public List<T> scroll(QueryBuilder queryBuilder, Class<T> clazz, Long time, String... indexs) throws Exception {
        if (queryBuilder == null) {
            queryBuilder = new MatchAllQueryBuilder();
        }
        List<T> list = new ArrayList<>();
        ScrollResponse<T> scrollResponse = createScroll(queryBuilder, clazz, time, 50);
        scrollResponse.getList().forEach(s -> list.add(s));
        String scrollId = scrollResponse.getScrollId();
        while (true){
            scrollResponse = queryScroll(clazz, time, scrollId);
            if(scrollResponse.getList() != null && scrollResponse.getList().size() != 0){
                scrollResponse.getList().forEach(s -> list.add(s));
                scrollId = scrollResponse.getScrollId();
            }else{
                break;
            }
        }
        return list;
    }

    
    public ScrollResponse<T> createScroll(QueryBuilder queryBuilder, Class<T> clazz, Long time, Integer size) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String indexname = settingsData.getIndexname();
        return createScroll(queryBuilder,clazz,time,size,indexname);
    }

    
    public ScrollResponse<T> createScroll(QueryBuilder queryBuilder, Class<T> clazz, Long time, Integer size, String... indexs) throws Exception {
        if (queryBuilder == null) {
            queryBuilder = new MatchAllQueryBuilder();
        }
        String[] indexname = indexs;
        List<T> list = new ArrayList<>();
        Scroll scroll = new Scroll(TimeValue.timeValueHours(time));
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.scroll(scroll);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        if(size == null || size == 0){
            searchSourceBuilder.size(Constant.DEFAULT_SCROLL_PERPAGE);
        }else{
            searchSourceBuilder.size(size);
        }
        searchSourceBuilder.query(queryBuilder);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        //???????????????????????????
        for (SearchHit hit : searchHits) {
            T t = JSONUtil.toBean(hit.getSourceAsString(), clazz);
            //???_id?????????????????????@ESID???????????????
            correctID(clazz, t, (M)hit.getId());
            list.add(t);
        }
        ScrollResponse<T> scrollResponse = new ScrollResponse(list,scrollId);
        return scrollResponse;
    }

    
    public ScrollResponse<T> queryScroll(Class<T> clazz, Long time , String scrollId) throws Exception {
        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
        Scroll scroll = new Scroll(TimeValue.timeValueHours(time));
        scrollRequest.scroll(scroll);
        SearchResponse searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
        scrollId = searchResponse.getScrollId();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        List<T> list = new ArrayList<>();
        for (SearchHit hit : searchHits) {
            T t = JSONUtil.toBean(hit.getSourceAsString(), clazz);
            //???_id?????????????????????@ESID???????????????
            correctID(clazz, t, (M)hit.getId());
            list.add(t);
        }
        ScrollResponse<T> scrollResponse = new ScrollResponse(list,scrollId);
        return scrollResponse;
    }

    
    public ClearScrollResponse clearScroll(String... scrollId) throws Exception {
        ClearScrollRequest request = new ClearScrollRequest();
        request.setScrollIds(Arrays.asList(scrollId));
        ClearScrollResponse response = client.clearScroll(request, RequestOptions.DEFAULT);
        return response;
    }


    
    public List<T> scroll(QueryBuilder queryBuilder, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return scroll(queryBuilder, clazz, Constant.DEFAULT_SCROLL_TIME, indexname);
    }

    
    public List<String> completionSuggest(String fieldName, String fieldValue, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return completionSuggest(fieldName, fieldValue, clazz, indexname);
    }

    
    public List<String> completionSuggest(String fieldName, String fieldValue, Class<T> clazz, String... indexs) throws Exception {
        String[] indexname = indexs;
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        CompletionSuggestionBuilder completionSuggestionBuilder = SuggestBuilders.completionSuggestion(fieldName)
                .text(fieldValue)
                .skipDuplicates(true)
                .size((Constant.COMPLETION_SUGGESTION_SIZE));

        suggestBuilder.addSuggestion("suggest_" + fieldName, completionSuggestionBuilder);
        searchSourceBuilder.suggest(suggestBuilder);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Suggest suggest = searchResponse.getSuggest();
        if (suggest == null) {
            return null;
        }
        CompletionSuggestion completionSuggestion = suggest.getSuggestion("suggest_" + fieldName);
        List<String> list = new ArrayList<>();
        for (CompletionSuggestion.Entry entry : completionSuggestion.getEntries()) {
            for (CompletionSuggestion.Entry.Option option : entry) {
                String suggestText = option.getText().string();
                list.add(suggestText);
            }
        }
        return list;
    }

    
    public List<String> phraseSuggest(String fieldName, String fieldValue, PhraseSuggestParam param, Class<T> clazz) throws Exception {
        SettingsData settingsData = elasticsearchIndex.getSettingsData(clazz);
        String[] indexname = settingsData.getSearchIndexNames();
        return phraseSuggest(fieldName, fieldValue, param, clazz, indexname);
    }



    
    public List<String> phraseSuggest(String fieldName, String fieldValue, PhraseSuggestParam param, Class<T> clazz, String... indexs) throws Exception {
        if(param == null){
            //????????????????????????????????????
            param = new PhraseSuggestParam(5,0,null,"always");
        }
        String[] indexname = indexs;
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        PhraseSuggestionBuilder phraseSuggestionBuilder = new PhraseSuggestionBuilder(fieldName);
        phraseSuggestionBuilder
                .text(fieldValue)
                .confidence(param.getConfidence())
                .size(Constant.COMPLETION_SUGGESTION_SIZE)
                .maxErrors(param.getMaxErrors())
                .addCandidateGenerator(new DirectCandidateGeneratorBuilder(fieldName).suggestMode(param.getSuggestMode()));
        if(param.getAnalyzer() != null) {
            phraseSuggestionBuilder.analyzer(param.getAnalyzer());
        }
        suggestBuilder.addSuggestion("suggest_" + fieldName, phraseSuggestionBuilder);
        searchSourceBuilder.suggest(suggestBuilder);
        SearchRequest searchRequest = new SearchRequest(indexname);
        searchRequest.source(searchSourceBuilder);
        log.info(searchSourceBuilder.toString());
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Suggest suggest = searchResponse.getSuggest();
        if (suggest == null) {
            return null;
        }
        PhraseSuggestion phraseSuggestion = suggest.getSuggestion("suggest_" + fieldName);
        List<String> list = new ArrayList<>();
        for (PhraseSuggestion.Entry entry : phraseSuggestion.getEntries()) {
            for (PhraseSuggestion.Entry.Option option : entry) {
                String suggestText = option.getText().string();
                list.add(suggestText);
            }
        }
        return list;
    }

    private int getTotalPages(long totalHits, int pageSize) {
        return pageSize == 0 ? 1 : (int) Math.ceil((double) totalHits / (double) pageSize);
    }

    public static class PhraseSuggestParam{
        private int maxErrors;
        private float confidence;
        private String analyzer;
        private String suggestMode;

        public PhraseSuggestParam(int maxErrors, float confidence, String analyzer, String suggestMode) {
            this.maxErrors = maxErrors;
            this.confidence = confidence;
            this.analyzer = analyzer;
            this.suggestMode = suggestMode;
        }

        public int getMaxErrors() {
            return maxErrors;
        }

        public void setMaxErrors(int maxErrors) {
            this.maxErrors = maxErrors;
        }

        public float getConfidence() {
            return confidence;
        }

        public void setConfidence(float confidence) {
            this.confidence = confidence;
        }

        public String getAnalyzer() {
            return analyzer;
        }

        public void setAnalyzer(String analyzer) {
            this.analyzer = analyzer;
        }

        public String getSuggestMode() {
            return suggestMode;
        }

        public void setSuggestMode(String suggestMode) {
            this.suggestMode = suggestMode;
        }
    }
}
