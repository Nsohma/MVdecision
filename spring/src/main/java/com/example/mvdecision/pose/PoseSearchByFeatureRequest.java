package com.example.mvdecision.pose;

public class PoseSearchByFeatureRequest {

    /**
     * クエリ側の特徴ベクトル
     * 例: "-1.916667,0.000000,-1.750000,0.000000,..."
     */
    private String featureVector;

    /**
     * 何件返すか（省略時は 10）
     */
    private Integer topK;

    public String getFeatureVector() {
        return featureVector;
    }

    public void setFeatureVector(String featureVector) {
        this.featureVector = featureVector;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
