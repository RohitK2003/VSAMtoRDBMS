package com.springaidemo;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
public class FileController {

    @Autowired
    private OutputService outputService;
    @Autowired
    private GeminiService sqlGenerationService;

    @PostMapping("/upload-csv")
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) {
        try {
            outputService.importCsv(file);
            return ResponseEntity.ok("CSV imported successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error importing CSV: " + e.getMessage());
        }
    }

    @PostMapping("/upload-data")
    public ResponseEntity<String> uploadDataZip(@RequestParam("file") MultipartFile file) {
        try {
            Path extractedDir = outputService.unzipData(file);
            return ResponseEntity.ok("Zip extracted successfully at: " + extractedDir.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error extracting Zip: " + e.getMessage());
        }
    }

    @PostMapping("/process-data")
    public ResponseEntity<String> processData() {
        try {
            outputService.processDataFiles();
            return ResponseEntity.ok("Data files processed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing data files: " + e.getMessage());
        }
    }
    @PostMapping("/generate")
    public ResponseEntity<String> generateSqlFiles() {

        try {

            sqlGenerationService.generateSqlForAllFiles();

            return ResponseEntity.ok(
                    "SQL files generated successfully in configured output directory."
            );

        } catch (Exception e) {

            return ResponseEntity.internalServerError()
                    .body("Error generating SQL files: " + e.getMessage());
        }
    }
}