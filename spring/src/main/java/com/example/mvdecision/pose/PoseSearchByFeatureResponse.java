package com.example.mvdecision.pose;

import java.util.List;

public class PoseSearchByFeatureResponse {

    /**
     * クエリに使った featureVector（エコーバックしておく）
     */
    private String featureVector;

    /**
     * 類似サンプルの一覧（距離が小さい順）
     */
    private List<PoseSearchResultDto> results;

    public String getFeatureVector() {
        return featureVector;
    }

    public void setFeatureVector(String featureVector) {
        this.featureVector = featureVector;
    }

    public List<PoseSearchResultDto> getResults() {
        return results;
    }

    public void setResults(List<PoseSearchResultDto> results) {
        this.results = results;
    }
}
