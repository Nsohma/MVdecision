// src/main/java/com/example/mvdecision/pose/PoseQueryController.java
package com.example.mvdecision.pose;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
@CrossOrigin(origins = "http://localhost:8080")
public class PoseQueryController {

    @PostMapping("/pose/feature")
    public PoseQueryResponse computeFeature(@RequestBody PoseQueryRequest request) {

        // 1) List<List<Double>> → double[][] に変換
        double[][] pts = PoseFeatureUtil.toArray(request.getKeypoints());

        // 2) 正規化
        double[][] normalized = PoseFeatureUtil.normalizeKeypoints(pts);

        // 3) 特徴量ベクトル生成（DB と同じ形式）
        String feature = PoseFeatureUtil.buildFeatureVector(normalized);

        // 4) クライアントに返却
        return new PoseQueryResponse(
                PoseFeatureUtil.toList(normalized),
                feature
        );
    }
}
