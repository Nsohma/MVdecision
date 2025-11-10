// src/main/java/com/example/mvdecision/dataset/DatasetController.java
package com.example.mvdecision.dataset;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    private final DatasetImportService datasetImportService;

    // コンストラクタインジェクション
    public DatasetController(DatasetImportService datasetImportService) {
        this.datasetImportService = datasetImportService;
    }

    @PostMapping("/uploadZip")
    public ResponseEntity<String> upload(@RequestPart("file") MultipartFile file) {
        try {
            // ★ ここは「呼ぶだけ」：返り値は void なので代入しない
            datasetImportService.importZip(file);

            return ResponseEntity.ok("Upload & import OK: " + file.getOriginalFilename());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .badRequest()
                    .body("Import failed: " + e.getMessage());
        }
    }
}
