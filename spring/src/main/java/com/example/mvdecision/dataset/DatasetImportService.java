package com.example.mvdecision.dataset;

import com.example.mvdecision.pose.PoseSample;
import com.example.mvdecision.pose.PoseSampleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Service
public class DatasetImportService {

    private final PoseSampleRepository poseSampleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path imageBaseDir = Paths.get("data", "datasets");

    @Autowired
    public DatasetImportService(PoseSampleRepository poseSampleRepository) throws IOException {
         this.poseSampleRepository = poseSampleRepository;
        Files.createDirectories(imageBaseDir);
    }

    /**
     * フロントから受け取った zip をパースして DB に保存。
     *
     * @return 保存した PoseSample の件数
     */
    public void importZip(MultipartFile zipFile) throws IOException {
        String datasetName = zipFile.getOriginalFilename(); // 例: A.zip
        if (datasetName == null) {
            datasetName = "unknown";
        }

        // この zip 専用のディレクトリ: data/datasets/A.zip/
        Path datasetDir = imageBaseDir.resolve(datasetName);
        Files.createDirectories(datasetDir);

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();                         // 例: "A001.png" or "A001_keypoints.json"
                String fileNameOnly = Paths.get(entryName).getFileName().toString();

                // 1) 画像ファイルならディスクに保存して終わり
                if (isImageFile(fileNameOnly)) {
                    Path target = datasetDir.resolve(fileNameOnly);
                    Files.createDirectories(target.getParent());
                    byte[] bytes = zis.readAllBytes();
                    Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.println("Saved image: " + target.toAbsolutePath());
                    continue;
                }

                // 2) JSON 以外は無視（__MACOSX や .DS_Store 対策）
                if (!fileNameOnly.endsWith(".json")) {
                    continue;
                }

                System.out.println("Parsing JSON entry: " + fileNameOnly);

                try {
                    String jsonText = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    JsonNode root = objectMapper.readTree(jsonText);

                    // ==== ここは既に書いていたロジックをそのまま使う想定 ====
                    // persons[0].keypoints[0..16] を読んで正規化 & 特徴量ベクトルに変換
                    JsonNode persons = root.path("persons");
                    if (!persons.isArray() || persons.isEmpty()) {
                        continue;
                    }
                    JsonNode person0 = persons.get(0);
                    JsonNode keypoints = person0.path("keypoints");
                    if (!keypoints.isArray() || keypoints.size() < 17) {
                        continue;
                    }

                    // ここで keypoints から 17点座標を読み取り → 正規化＆特徴量ベクトル作成
                    // （詳細ロジックは前に書いたものを流用）
                    String normalized = buildNormalizedKeypointsJson(keypoints);
                    String featureVectorJson = buildFeatureVectorJson(keypoints);

                    // ==== 画像ファイル名とパスを組み立てる ====
                    // 例: "A001_keypoints.json" → ベース "A001" → 画像 "A001.png"
                    String stem = fileNameOnly;
                    if (stem.endsWith("_keypoints.json")) {
                        stem = stem.substring(0, stem.length() - "_keypoints.json".length());
                    } else if (stem.endsWith(".json")) {
                        stem = stem.substring(0, stem.length() - ".json".length());
                    }
                    String imageFileName = stem + ".png";  // 画像が PNG の前提
                    Path imagePath = datasetDir.resolve(imageFileName);

                    PoseSample sample = new PoseSample();
                    sample.setDatasetName(datasetName);
                    sample.setImageFileName(imageFileName);
                    sample.setImagePath(imagePath.toString());
                    sample.setRawJson(jsonText);
                    sample.setNormalizedKeypointsJson(normalized);
                    sample.setFeatureVector(featureVectorJson);

                    poseSampleRepository.save(sample);

                } catch (Exception ex) {
                    System.err.println("Failed to parse JSON in entry: " + fileNameOnly);
                    ex.printStackTrace();
                    // 壊れた JSON はスキップして続行
                }
            }
        }
    }


    // ==== ダミー実装（前に書いた正規化ロジックに置き換えてOK） ====
    private String buildNormalizedKeypointsJson(JsonNode keypoints) {
        // 実際は 17点を [x,y] で読み取って正規化した配列を JSON にする
        return keypoints.toString();
    }

    private String buildFeatureVectorJson(JsonNode keypoints) {
        // 実際は正規化座標→1次元ベクトル化したものを JSON にする
        return keypoints.toString();
    }



    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }


    /** persons[] から平均 keypoint_scores 最大の person を選ぶ */
    private JsonNode pickBestPerson(JsonNode personsNode) {
        JsonNode best = null;
        double bestScore = -Double.MAX_VALUE;

        for (JsonNode person : personsNode) {
            JsonNode scores = person.get("keypoint_scores");
            double avg = 0.0;
            int n = 0;
            if (scores != null && scores.isArray()) {
                for (JsonNode s : scores) {
                    avg += s.asDouble(0.0);
                    n++;
                }
            }
            if (n > 0) avg /= n;
            if (avg > bestScore) {
                bestScore = avg;
                best = person;
            }
        }
        return best;
    }

    /** keypoints 配列から 17点 [ [x,y], ... ] を取り出す（足りなければ最後を複製） */
    private double[][] extract17Keypoints(JsonNode kpsNode) {
        List<double[]> list = new ArrayList<>();
        for (JsonNode kp : kpsNode) {
            if (!kp.isArray() || kp.size() < 2) continue;
            double x = kp.get(0).asDouble(0.0);
            double y = kp.get(1).asDouble(0.0);
            list.add(new double[]{x, y});
        }
        while (list.size() < 17) {
            if (list.isEmpty()) {
                list.add(new double[]{0.0, 0.0});
            } else {
                double[] last = list.get(list.size() - 1);
                list.add(new double[]{last[0], last[1]});
            }
        }
        if (list.size() > 17) {
            list = list.subList(0, 17);
        }

        double[][] pts = new double[17][2];
        for (int i = 0; i < 17; i++) {
            pts[i][0] = list.get(i)[0];
            pts[i][1] = list.get(i)[1];
        }
        return pts;
    }

    /**
     * 17点のスケルトンを正規化:
     *  - 腰中心(11,12)を原点になるよう平行移動
     *  - 両肩中心〜両足首中心の距離を 1 になるようスケーリング
     */
    private double[][] normalizeKeypoints(double[][] pts) {
        final int LEFT_HIP = 11;
        final int RIGHT_HIP = 12;
        final int LEFT_SHOULDER = 5;
        final int RIGHT_SHOULDER = 6;
        final int LEFT_ANKLE = 15;
        final int RIGHT_ANKLE = 16;

        double[][] out = new double[17][2];

        // root (腰中心)
        double rootX = (pts[LEFT_HIP][0] + pts[RIGHT_HIP][0]) / 2.0;
        double rootY = (pts[LEFT_HIP][1] + pts[RIGHT_HIP][1]) / 2.0;

        // 平行移動
        double[][] centered = new double[17][2];
        for (int i = 0; i < 17; i++) {
            centered[i][0] = pts[i][0] - rootX;
            centered[i][1] = pts[i][1] - rootY;
        }

        // 肩中心 & 足首中心
        double shX = (centered[LEFT_SHOULDER][0] + centered[RIGHT_SHOULDER][0]) / 2.0;
        double shY = (centered[LEFT_SHOULDER][1] + centered[RIGHT_SHOULDER][1]) / 2.0;
        double anX = (centered[LEFT_ANKLE][0] + centered[RIGHT_ANKLE][0]) / 2.0;
        double anY = (centered[LEFT_ANKLE][1] + centered[RIGHT_ANKLE][1]) / 2.0;

        double hdx = anX - shX;
        double hdy = anY - shY;
        double height = Math.sqrt(hdx * hdx + hdy * hdy);
        if (height < 1e-6) {
            height = 1.0;
        }

        // スケール
        for (int i = 0; i < 17; i++) {
            out[i][0] = centered[i][0] / height;
            out[i][1] = centered[i][1] / height;
        }
        return out;
    }

    /** 正規化済み17点を 1次元ベクトル "x0,y0,x1,y1,..." のように文字列化 */
    private String flattenFeatureVector(double[][] norm) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < norm.length; i++) {
            if (!first) sb.append(',');
            sb.append(norm[i][0]).append(',').append(norm[i][1]);
            first = false;
        }
        return sb.toString();
    }

    /**
     * JSONの image_path があればそれを優先し、
     * 無ければ zip エントリ名から "A001_keypoints.json" → "A001.png" のように推定する。
     */
    private String guessImageFileNameFromJsonOrEntry(JsonNode root, String entryName) {
        JsonNode imagePathNode = root.get("image_path");
        if (imagePathNode != null && imagePathNode.isTextual()) {
            String path = imagePathNode.asText();
            int idx = path.replace('\\', '/').lastIndexOf('/');
            return (idx >= 0) ? path.substring(idx + 1) : path;
        }

        String fileName = entryName;
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) fileName = fileName.substring(slash + 1);
        int dot = fileName.lastIndexOf('.');
        String base = (dot >= 0) ? fileName.substring(0, dot) : fileName;
        if (base.endsWith("_keypoints")) {
            base = base.substring(0, base.length() - "_keypoints".length());
        }
        return base + ".png";
    }
}
