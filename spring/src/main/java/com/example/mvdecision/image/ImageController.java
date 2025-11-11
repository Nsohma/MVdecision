package com.example.mvdecision.image;

import com.example.mvdecision.pose.PoseSample;
import com.example.mvdecision.pose.PoseSampleRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final PoseSampleRepository poseSampleRepository;

    public ImageController(PoseSampleRepository poseSampleRepository) {
        this.poseSampleRepository = poseSampleRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> getImage(@PathVariable Long id) {
        // 1) DBからレコード取得
        PoseSample sample = poseSampleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PoseSample not found: " + id));

        // 2) DBに保存されているパスを Path に変換
        Path path = Paths.get(sample.getImagePath());

        // 相対パスの場合はプロジェクトルートを基準に解決
        if (!path.isAbsolute()) {
            Path baseDir = Paths.get(System.getProperty("user.dir")); // プロジェクトのカレント
            path = baseDir.resolve(path).normalize();
        }

        // 3) ファイル存在チェック
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image file not found: " + path);
        }

        try {
            Resource resource = new UrlResource(path.toUri());

            // 4) Content-Type 判定（png/jpgなど）
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                // 拡張子から雑に決めてもOK
                String lower = path.getFileName().toString().toLowerCase();
                if (lower.endsWith(".png")) {
                    contentType = "image/png";
                } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else {
                    contentType = "application/octet-stream";
                }
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    // 必要ならキャッシュコントロール（お好みで）
                    .cacheControl(CacheControl.noCache())
                    .body(resource);

        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid image path", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read image file", e);
        }
    }
}
