// src/main/java/com/example/mvdecision/pose/PoseQueryResponse.java
package com.example.mvdecision.pose;

import java.util.List;

public class PoseQueryResponse {

    /** 正規化済み 17点 [[nx,ny], ...] */
    private List<List<Double>> normalizedKeypoints;

    /** フィーチャベクトル（DBと同じ文字列表現） */
    private String featureVector;

    public PoseQueryResponse() {}

    public PoseQueryResponse(List<List<Double>> normalizedKeypoints, String featureVector) {
        this.normalizedKeypoints = normalizedKeypoints;
        this.featureVector = featureVector;
    }

    public List<List<Double>> getNormalizedKeypoints() {
        return normalizedKeypoints;
    }

    public void setNormalizedKeypoints(List<List<Double>> normalizedKeypoints) {
        this.normalizedKeypoints = normalizedKeypoints;
    }

    public String getFeatureVector() {
        return featureVector;
    }

    public void setFeatureVector(String featureVector) {
        this.featureVector = featureVector;
    }
}
