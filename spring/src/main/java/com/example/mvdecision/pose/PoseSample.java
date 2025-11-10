// src/main/java/com/example/mvdecision/pose/PoseSample.java

package com.example.mvdecision.pose;

import jakarta.persistence.*;

@Entity
@Table(name = "pose_sample")
public class PoseSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** アップロードした zip ファイル名（例: A.zip） */
    private String datasetName;

    /** 画像ファイル名（例: A001.png） */
    private String imageFileName;

    /** 画像をサーバーに保存したパス（例: data/datasets/A.zip/A001.png） */
    @Column(length = 1024)
    private String imagePath;

    /** 元の JSON 全文 */
    @Lob
    private String rawJson;

    /** 正規化後の keypoints JSON */
    @Lob
    private String normalizedKeypointsJson;

    /** 類似度検索用の特徴ベクトル (JSON 文字列など) */
    @Lob
    private String featureVector;

    // --- getter / setter ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getImageFileName() { return imageFileName; }
    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public String getNormalizedKeypointsJson() { return normalizedKeypointsJson; }
    public void setNormalizedKeypointsJson(String normalizedKeypointsJson) {
        this.normalizedKeypointsJson = normalizedKeypointsJson;
    }

    public String getFeatureVector() { return featureVector; }
    public void setFeatureVector(String featureVector) { this.featureVector = featureVector; }
}
