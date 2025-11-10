package com.example.mvdecision.dataset;

import com.example.mvdecision.pose.PoseSample;
import com.example.mvdecision.pose.PoseSampleRepository;
import com.example.mvdecision.pose.PoseFeatureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DatasetImportService {

    private final PoseSampleRepository poseSampleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // data/datasets の下に zip ごとのディレクトリを作る
    private final Path imageBaseDir = Paths.get("data", "datasets");

    @Autowired
    public DatasetImportService(PoseSampleRepository poseSampleRepository) throws IOException {
        this.poseSampleRepository = poseSampleRepository;
        Files.createDirectories(imageBaseDir);
    }

    /**
     * フロントから受け取った zip をパースして DB に保存。
     * 画像は data/datasets/{zip名}/ に保存し、そのパスを image_path に入れる。
     */
    public void importZip(MultipartFile zipFile) throws IOException {
        String datasetName = zipFile.getOriginalFilename(); // 例: B.zip
        if (datasetName == null || datasetName.isBlank()) {
            datasetName = "unknown";
        }

        // この zip 専用のディレクトリ: data/datasets/B.zip/
        Path datasetDir = imageBaseDir.resolve(datasetName);
        Files.createDirectories(datasetDir);

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName(); // 例: "B010.png" or "subdir/B010_keypoints.json"
                String fileNameOnly = Paths.get(entryName).getFileName().toString();

                // 1) 画像ファイルならディスクに保存して終わり
                if (isImageFile(fileNameOnly)) {
                    Path target = datasetDir.resolve(fileNameOnly);
                    Files.createDirectories(target.getParent());
                    byte[] bytes = zis.readAllBytes();
                    Files.write(target, bytes,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.println("Saved image: " + target.toAbsolutePath());
                    continue;
                }

                // 2) JSON 以外は無視（__MACOSX, .DS_Store など）
                if (!fileNameOnly.endsWith(".json")) {
                    continue;
                }

                System.out.println("Parsing JSON entry: " + fileNameOnly);

                try {
                    String jsonText = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    JsonNode root = objectMapper.readTree(jsonText);

                    // persons[] から「平均 keypoint_scores 最大」の person を選ぶ
                    JsonNode persons = root.path("persons");
                    if (!persons.isArray() || persons.isEmpty()) {
                        continue;
                    }
                    JsonNode bestPerson = pickBestPerson(persons);
                    if (bestPerson == null) {
                        continue;
                    }

                    JsonNode keypoints = bestPerson.path("keypoints");
                    if (!keypoints.isArray() || keypoints.size() == 0) {
                        continue;
                    }

                    // === 17 点を取り出して double[][] に変換 ===
                    double[][] pts17 = extract17Keypoints(keypoints);

                    // === PoseFeatureUtil で正規化 & 特徴量ベクトル化 ===
                    double[][] norm = PoseFeatureUtil.normalizeKeypoints(pts17);
                    String featureVector = PoseFeatureUtil.buildFeatureVector(norm);

                    // 正規化した 17×2 を JSON 文字列として保存
                    String normalizedJson = objectMapper.writeValueAsString(norm);

                    // ==== 画像ファイル名とローカルパス ====
                    // JSON に image_path があればそれを優先し、なければ
                    // "B010_keypoints.json" → "B010.png" のように推定
                    String imageFileName = guessImageFileNameFromJsonOrEntry(root, entryName);
                    Path imagePath = datasetDir.resolve(imageFileName);

                    PoseSample sample = new PoseSample();
                    sample.setDatasetName(datasetName);              // 例: "B.zip"
                    sample.setImageFileName(imageFileName);          // 例: "B010.png"
                    sample.setImagePath(imagePath.toString());       // 例: "data/datasets/B.zip/B010.png"
                    sample.setRawJson(jsonText);                     // 元 JSON 全体
                    sample.setNormalizedKeypointsJson(normalizedJson); // 正規化済み 17×2
                    sample.setFeatureVector(featureVector);          // "x0,y0,x1,y1,..." 形式

                    poseSampleRepository.save(sample);

                } catch (Exception ex) {
                    System.err.println("Failed to parse JSON in entry: " + fileNameOnly);
                    ex.printStackTrace();
                    // 壊れた JSON はスキップして続行
                }
            }
        }
    }

    // ----------------- ヘルパーメソッド群 -----------------

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
            if (n > 0) {
                avg /= n;
            }
            if (avg > bestScore) {
                bestScore = avg;
                best = person;
            }
        }
        return best;
    }

    /** keypoints 配列から 17点 [ [x,y], ... ] を取り出す（足りなければ最後を複製、余れば先頭17） */
    private double[][] extract17Keypoints(JsonNode kpsNode) {
        List<double[]> list = new ArrayList<>();
        for (JsonNode kp : kpsNode) {
            if (!kp.isArray() || kp.size() < 2) continue;
            double x = kp.get(0).asDouble(0.0);
            double y = kp.get(1).asDouble(0.0);
            list.add(new double[]{x, y});
        }
        // 足りない分は最後の点を複製
        while (list.size() < 17) {
            if (list.isEmpty()) {
                list.add(new double[]{0.0, 0.0});
            } else {
                double[] last = list.get(list.size() - 1);
                list.add(new double[]{last[0], last[1]});
            }
        }
        // 多すぎる分は先頭17個だけ
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
     * JSONの image_path があればそのファイル名を使う。
     * 無ければ zip エントリ名から "B010_keypoints.json" → "B010.png" のように推定。
     */
    private String guessImageFileNameFromJsonOrEntry(JsonNode root, String entryName) {
        // 1. JSON に image_path があれば、その末尾のファイル名だけ抜き出す
        JsonNode imagePathNode = root.get("image_path");
        if (imagePathNode != null && imagePathNode.isTextual()) {
            String path = imagePathNode.asText();
            // パス区切りを / に統一して、最後の / 以降をファイル名とみなす
            String normalized = path.replace('\\', '/');
            int idx = normalized.lastIndexOf('/');
            return (idx >= 0) ? normalized.substring(idx + 1) : normalized;
        }

        // 2. 無い場合はエントリ名から推定
        String fileName = entryName;
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        int dot = fileName.lastIndexOf('.');
        String base = (dot >= 0) ? fileName.substring(0, dot) : fileName;
        if (base.endsWith("_keypoints")) {
            base = base.substring(0, base.length() - "_keypoints".length());
        }
        return base + ".png";
    }
}
