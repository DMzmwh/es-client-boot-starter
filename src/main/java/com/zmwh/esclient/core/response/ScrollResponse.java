package com.zmwh.esclient.core.response;

import java.util.List;

/**
 * @description:
 * @author: dmzmwh
 * @create: 2019-10-15 17:58
 **/
public class ScrollResponse<T> {
    private List<T> list;
    private String scrollId;

    public ScrollResponse(List<T> list, String scrollId) {
        this.list = list;
        this.scrollId = scrollId;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public String getScrollId() {
        return scrollId;
    }

    public void setScrollId(String scrollId) {
        this.scrollId = scrollId;
    }
}
