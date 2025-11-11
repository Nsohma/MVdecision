package com.example.mvdecision.dataset;

import com.example.mvdecision.pose.PoseSample;
import com.example.mvdecision.pose.PoseSampleRepository;
import com.example.mvdecision.pose.PoseFeatureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class DatasetImportService {

    private final PoseSampleRepository poseSampleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ★ ここを String → Path にして、デフォルトの保存ルートを固定
    //   これで常に data/datasets 配下に保存されます
    private final Path datasetRoot = Paths.get("data", "datasets");

    private static final Pattern CUT_CODE_PATTERN = Pattern.compile("C\\d{3,4}");

    private String extractCutCodeFromSourcePath(String sourceImagePath) {
        if (sourceImagePath == null) return null;
        Matcher m = CUT_CODE_PATTERN.matcher(sourceImagePath);
        return m.find() ? m.group() : null; // 例: "C392"
    }

    private String zipBaseName(String zipFileName) {
        if (zipFileName == null) return "unknown";
        int dot = zipFileName.lastIndexOf('.');
        return (dot > 0) ? zipFileName.substring(0, dot) : zipFileName; // "B.zip" -> "B"
    }

    @Autowired
    public DatasetImportService(PoseSampleRepository poseSampleRepository) throws IOException {
        this.poseSampleRepository = poseSampleRepository;
        // ★ ここで data/datasets を必ず作っておく
        Files.createDirectories(datasetRoot);
    }

    /**
     * フロントから受け取った zip をパースして DB に保存。
     * 画像は data/datasets/{cutCode}_{zipBase}/ に保存し、そのパスを image_path に入れる。
     */
    public void importZip(MultipartFile zipFile) throws IOException {
        String zipName = StringUtils.cleanPath(Objects.requireNonNull(zipFile.getOriginalFilename()));
        String zipBase = zipBaseName(zipName);
        if (zipBase == null || zipBase.isBlank()) zipBase = "unknown";
    
        // 1) アップロードを一時ファイルへ
        Path tmp = Files.createTempFile("mvdecision-upload-", ".zip");
        zipFile.transferTo(tmp.toFile());
    
        // 画像ファイル名 -> 最終保存先パス の対応を貯める
        Map<String, Path> imageDestMap = new HashMap<>();
    
        try (ZipFile zf = new ZipFile(tmp.toFile())) {
        
            // ---------- パス1：JSON だけ読む ----------
            Enumeration<? extends ZipEntry> entries1 = zf.entries();
            while (entries1.hasMoreElements()) {
                ZipEntry e = entries1.nextElement();
                if (e.isDirectory()) continue;
            
                String entryName = e.getName();
                String fileNameOnly = Paths.get(entryName).getFileName().toString();
                if (!fileNameOnly.toLowerCase().endsWith(".json")) continue;           // JSON 以外スキップ
                if (entryName.startsWith("__MACOSX/")) continue;                        // mac のメタは無視
            
                try (InputStream in = zf.getInputStream(e)) {
                    String jsonText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonNode root = objectMapper.readTree(jsonText);
                
                    // 元のフルパス（例: .../C392/B/B001.png）から C*** を抽出
                    String sourceImagePath = null;
                    JsonNode node = root.get("image_path");
                    if (node != null && node.isTextual()) sourceImagePath = node.asText();
                    String cutCode = extractCutCodeFromSourcePath(sourceImagePath);
                    if (cutCode == null) cutCode = "unknown";
                
                    // 保存先ディレクトリ: data/datasets/C392_B
                    Path datasetDir = datasetRoot.resolve(cutCode).resolve(zipBase);
                    Files.createDirectories(datasetDir);
                
                    // 対応する画像ファイル名（B001.png など）
                    String imageFileName = guessImageFileNameFromJsonOrEntry(root, entryName);
                    Path imagePath = datasetDir.resolve(imageFileName);
                
                    // === 17点を取り出し → 正規化 → 特徴量 ===
                    JsonNode persons = root.path("persons");
                    if (!persons.isArray() || persons.isEmpty()) continue;
                    JsonNode best = pickBestPerson(persons);
                    if (best == null) continue;
                    JsonNode kps = best.path("keypoints");
                    if (!kps.isArray() || kps.size() == 0) continue;
                
                    double[][] pts17 = extract17Keypoints(kps);
                    double[][] norm = PoseFeatureUtil.normalizeKeypoints(pts17);
                    String featureVector = PoseFeatureUtil.buildFeatureVector(norm);
                    String normalizedJson = objectMapper.writeValueAsString(norm);
                
                    // DB 登録
                    PoseSample sample = new PoseSample();
                    sample.setDatasetName(zipName);
                    sample.setImageFileName(imageFileName);
                    sample.setImagePath(imagePath.toString());   // アプリ内の配置先
                    sample.setSourceImagePath(sourceImagePath);  // 元データのフルパス
                    sample.setRawJson(jsonText);
                    sample.setNormalizedKeypointsJson(normalizedJson);
                    sample.setFeatureVector(featureVector);
                    poseSampleRepository.save(sample);
                
                    // 画像の最終保存先を覚えておく
                    imageDestMap.put(imageFileName.toLowerCase(), imagePath);
                } catch (Exception ex) {
                    System.err.println("Skip broken JSON: " + fileNameOnly);
                    ex.printStackTrace();
                }
            }
        
            // ---------- パス2：画像だけコピー ----------
            Enumeration<? extends ZipEntry> entries2 = zf.entries();
            while (entries2.hasMoreElements()) {
                ZipEntry e = entries2.nextElement();
                if (e.isDirectory()) continue;
            
                String entryName = e.getName();
                String fileNameOnly = Paths.get(entryName).getFileName().toString();
                if (entryName.startsWith("__MACOSX/")) continue;
                if (!isImageFile(fileNameOnly)) continue;  // png/jpg/jpeg 以外は無視
            
                Path dest = imageDestMap.get(fileNameOnly.toLowerCase());
                if (dest == null) {
                    // 対応する JSON が無かった画像は unknown に避難
                    Path fallbackDir = datasetRoot.resolve("unknown_" + zipBase);
                    Files.createDirectories(fallbackDir);
                    dest = fallbackDir.resolve(fileNameOnly);
                }
                Files.createDirectories(dest.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) {}
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
