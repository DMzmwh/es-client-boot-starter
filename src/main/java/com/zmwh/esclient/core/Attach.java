package com.zmwh.esclient.core;

import lombok.Data;

/**
 * 提供更强的查询功能可选条件
 * @author: dmzmwh
 * @create: 2019-10-14 10:42
 **/
@Data
public class Attach{
    private PageSortHighLight pageSortHighLight = null;
    private String[] includes;
    private String[] excludes;
    private String routing;
    private boolean searchAfter = false;
    private boolean trackTotalHits = false;
    private Object[] sortValues;
}
