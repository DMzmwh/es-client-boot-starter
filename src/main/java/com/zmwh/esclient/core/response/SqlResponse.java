package com.zmwh.esclient.core.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SqlResponse {
    @JsonProperty("columns")
    private List<ColumnsDTO> columns;
    @JsonProperty("rows")
    private List<List<String>> rows;
    @Data
    public static class ColumnsDTO {
        @JsonProperty("name")
        private String name;
        @JsonProperty("type")
        private String type;
    }
}
