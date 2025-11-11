package com.example.mvdecision.pose;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PoseSearchService {

    private final PoseSampleRepository poseSampleRepository;

    public PoseSearchService(PoseSampleRepository poseSampleRepository) {
        this.poseSampleRepository = poseSampleRepository;
    }

    public PoseSearchByFeatureResponse searchByFeature(PoseSearchByFeatureRequest request) {
        String queryFeatureVector = request.getFeatureVector();
        if (queryFeatureVector == null || queryFeatureVector.isBlank()) {
            throw new IllegalArgumentException("featureVector is required");
        }

        int topK = (request.getTopK() != null && request.getTopK() > 0)
                ? request.getTopK()
                : 10;

        // クエリの featureVector を double[] にパース
        double[] queryVec = parseFeatureVector(queryFeatureVector);

        // 全サンプルを取得して、距離を計算
        List<PoseSample> allSamples = poseSampleRepository.findAll();
        List<PoseSearchResultDto> scored = new ArrayList<>();

        for (PoseSample sample : allSamples) {
            String fv = sample.getFeatureVector();
            if (fv == null || fv.isBlank()) {
                continue;
            }

            double[] sampleVec;
            try {
                sampleVec = parseFeatureVector(fv);
            } catch (NumberFormatException ex) {
                // パースできない壊れたデータはスキップ
                continue;
            }

            if (sampleVec.length != queryVec.length) {
                // 次元が合わないものもスキップ
                continue;
            }

            double dist = euclideanDistance(queryVec, sampleVec);

            PoseSearchResultDto dto = new PoseSearchResultDto();
            dto.setId(sample.getId());
            dto.setDatasetName(sample.getDatasetName());
            dto.setImageFileName(sample.getImageFileName());
            dto.setImagePath(sample.getImagePath());
            dto.setDistance(dist);
            dto.setSourceImagePath(sample.getSourceImagePath());
            dto.setDisplayPath(buildDisplayPath(sample));
            scored.add(dto);
        }

        // 距離が小さい順にソートして、topK 件に絞る
        List<PoseSearchResultDto> topList = scored.stream()
                .sorted(Comparator.comparingDouble(PoseSearchResultDto::getDistance))
                .limit(topK)
                .collect(Collectors.toList());

        PoseSearchByFeatureResponse response = new PoseSearchByFeatureResponse();
        response.setFeatureVector(queryFeatureVector);
        response.setResults(topList);
        return response;
    }

    /**
     * "x0,y0,x1,y1,..." を double[] に変換
     */
    private double[] parseFeatureVector(String featureVector) {
        String[] tokens = featureVector.split(",");
        List<Double> values = new ArrayList<>();
        for (String t : tokens) {
            String s = t.trim();
            if (s.isEmpty()) continue;
            values.add(Double.parseDouble(s));
        }
        double[] arr = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            arr[i] = values.get(i);
        }
        return arr;
    }

    /**
     * ユークリッド距離
     */
    private double euclideanDistance(double[] a, double[] b) {
        if (a.length != b.length) {
            return Double.POSITIVE_INFINITY;
        }
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }


    private String buildDisplayPath(PoseSample sample) {
        String src = sample.getSourceImagePath();
        if (src == null || src.isBlank()) {
            // 何もなければ従来の表示にフォールバック
            return sample.getDatasetName() + "/" + sample.getImageFileName();
        }
        // パス区切りを / にそろえる
        String normalized = src.replace('\\', '/');
        String[] parts = normalized.split("/");

        // 末尾3要素を "C001/B/B005.png" の形で返す
        if (parts.length >= 3) {
            int n = parts.length;
            String parts_n1 = parts[n-1].substring(0, parts[n-1].length()-4);
            return parts[n-3] + "・"+ parts_n1;
        } else if (parts.length == 2) {
            return parts[0] + "/" + parts[1];
        } else {
            return normalized;
        }
    }
}


