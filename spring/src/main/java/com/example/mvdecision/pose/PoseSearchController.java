package com.example.mvdecision.pose;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query/pose")
public class PoseSearchController {

    private final PoseSearchService poseSearchService;

    public PoseSearchController(PoseSearchService poseSearchService) {
        this.poseSearchService = poseSearchService;
    }

    /**
     * feature_vector ベースの類似検索
     *
     * 例:
     * curl -X POST http://localhost:8081/api/query/pose/search \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *         "featureVector": "-1.916667,0.000000,-1.750000,0.000000,...",
     *         "topK": 10
     *       }'
     */
    @PostMapping("/search")
    public PoseSearchByFeatureResponse searchByFeature(@RequestBody PoseSearchByFeatureRequest request) {
        return poseSearchService.searchByFeature(request);
    }
}
