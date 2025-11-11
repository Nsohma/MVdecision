package com.example.mvdecision.pose;

import jakarta.persistence.*;

@Entity
@Table(name = "pose_sample")
public class PoseSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // どの zip から来たか（例: A.zip / B.zip）
    @Column(name = "dataset_name", length = 255)
    private String datasetName;

    // 画像ファイル名（例: A001.png）
    @Column(name = "image_file_name", length = 255)
    private String imageFileName;

    // サーバー上のフルパス
    @Column(name = "image_path", length = 1024)
    private String imagePath;

    // ★ 追加: 元JSONに書かれていた image_path（元データの保存場所）
    @Column(name = "source_image_path", length = 2048)
    private String sourceImagePath;

    @Column(name = "cut_code", length = 32)
    private String cutCode; // 例: "C392"

    // 正規化済み keypoints の JSON ([[x,y], ...])
    @Lob
    @Column(name = "normalized_keypoints_json", columnDefinition = "TEXT")
    private String normalizedKeypointsJson;

    // ★ここが問題のカラム。TEXT にする
    @Lob
    @Column(name = "feature_vector", columnDefinition = "TEXT")
    private String featureVector;

    // 元の JSON 丸ごと
    @Lob
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    // ====== getter / setter ======

    public Long getId() {
        return id;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getNormalizedKeypointsJson() {
        return normalizedKeypointsJson;
    }

    public void setNormalizedKeypointsJson(String normalizedKeypointsJson) {
        this.normalizedKeypointsJson = normalizedKeypointsJson;
    }

    public String getFeatureVector() {
        return featureVector;
    }

    public void setFeatureVector(String featureVector) {
        this.featureVector = featureVector;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public String getSourceImagePath() {
        return sourceImagePath;
    }

    public void setSourceImagePath(String sourceImagePath) {
        this.sourceImagePath = sourceImagePath;
    }


    public String getCutCode() { return cutCode; }
    public void setCutCode(String cutCode) { this.cutCode = cutCode; }

}
