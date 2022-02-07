package com.zmwh.esclient.core.response;

import lombok.Data;

import java.util.List;

/**
 * uri返回json串反序列化对象
 * @author: dmzmwh
 * @create: 2019-10-10 10:45
 **/
@Data
public class UriResponse {


    /**
     * took : 9
     * timed_out : false
     * _shards : {"total":5,"successful":5,"skipped":0,"failed":0}
     * hits : {"total":{"value":1,"relation":"eq"},"max_score":0.9808292,"hits":[{"_index":"index","_type":"main4","_id":"aaa","_score":0.9808292,"_source":{"end_date":null,"proposal_no":"aaa","sum_amount":0,"business_nature_name":"aaaaaa2","operate_date":null,"sum_premium":0,"appli_name":null,"business_nature":null,"insured_code":null,"appli_code":null,"serialVersionUID":1,"operate_date_format":null,"insured_name":null,"com_code":null,"risk_code":null,"risk_name":null,"start_date":null}}]}
     */

    private int took;
    private boolean timed_out;
    private ShardsBean _shards;
    private HitsBeanX hits;

    @Data
    public static class ShardsBean {
        /**
         * total : 5
         * successful : 5
         * skipped : 0
         * failed : 0
         */

        private int total;
        private int successful;
        private int skipped;
        private int failed;

    }

    @Data
    public static class HitsBeanX {
        /**
         * total : {"value":1,"relation":"eq"}
         * max_score : 0.9808292
         * hits : [{"_index":"index","_type":"main4","_id":"aaa","_score":0.9808292,"_source":{"end_date":null,"proposal_no":"aaa","sum_amount":0,"business_nature_name":"aaaaaa2","operate_date":null,"sum_premium":0,"appli_name":null,"business_nature":null,"insured_code":null,"appli_code":null,"serialVersionUID":1,"operate_date_format":null,"insured_name":null,"com_code":null,"risk_code":null,"risk_name":null,"start_date":null}}]
         */

        private TotalBean total;
        private double max_score;
        private List<HitsBean> hits;


        @Data
        public static class TotalBean {
            /**
             * value : 1
             * relation : eq
             */

            private int value;
            private String relation;

            public int getValue() {
                return value;
            }

            public void setValue(int value) {
                this.value = value;
            }

            public String getRelation() {
                return relation;
            }

            public void setRelation(String relation) {
                this.relation = relation;
            }
        }

        @Data
        public static class HitsBean {
            /**
             * _index : index
             * _type : main4
             * _id : aaa
             * _score : 0.9808292
             * _source : {"end_date":null,"proposal_no":"aaa","sum_amount":0,"business_nature_name":"aaaaaa2","operate_date":null,"sum_premium":0,"appli_name":null,"business_nature":null,"insured_code":null,"appli_code":null,"serialVersionUID":1,"operate_date_format":null,"insured_name":null,"com_code":null,"risk_code":null,"risk_name":null,"start_date":null}
             */

            private String _index;
            private String _type;
            private String _id;
            private double _score;
            private Object _source;
        }
    }
}
