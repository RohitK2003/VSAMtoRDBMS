package com.springaidemo;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/copybook")
public class DemoController {

    @Autowired
    private CopybookService copybookService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadZip(@RequestParam("file") MultipartFile file) {
        try {
            Path extractedDir = copybookService.unzipCopybooks(file);
            return new ResponseEntity<>("Zip extracted successfully!", HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error extracting Zip!", HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS);
        }
    }

    @GetMapping("/processExtracted")
    public ResponseEntity<String> processExtractedFolder() {
        try {
            Path outputDir = copybookService.processCopybooks();
            return new ResponseEntity<>("Processed successfully", HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error processing copybooks!", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/list-copybooks")
    public ResponseEntity<List<String>> listCopybookFiles() {
        try {
            List<String> copybookFiles = copybookService.getAllCopybookFiles();
            
            if (copybookFiles.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }
            
            return ResponseEntity.ok(copybookFiles);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}