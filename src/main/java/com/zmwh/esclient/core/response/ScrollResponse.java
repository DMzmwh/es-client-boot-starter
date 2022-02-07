package com.zmwh.esclient.core.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @description:
 * @author: dmzmwh
 * @create: 2019-10-15 17:58
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrollResponse<T> {
    private List<T> list;
    private String scrollId;
}
