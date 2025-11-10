// src/main/java/com/example/mvdecision/pose/PoseQueryRequest.java
package com.example.mvdecision.pose;

import java.util.List;

public class PoseQueryRequest {

    /**
     * 17点のスケルトン。
     * 形式: [[x0,y0], [x1,y1], ..., [x16,y16]]
     */
    private List<List<Double>> keypoints;

    public List<List<Double>> getKeypoints() {
        return keypoints;
    }

    public void setKeypoints(List<List<Double>> keypoints) {
        this.keypoints = keypoints;
    }
}
