package com.springaidemo;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/copybook")
public class DemoController {

    @Autowired
    private CopybookService copybookService;

    @PostMapping("/upload")
    public String uploadZip(@RequestParam("file") MultipartFile file) {
        try {
            Path extractedDir = copybookService.unzipCopybooks(file);
            return "Zip extracted successfully at: "
                    + extractedDir.toAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error extracting Zip: " + e.getMessage();
        }
    }

    @GetMapping("/processExtracted")
    public String processExtractedFolder() {
        try {
            Path outputDir = copybookService.processCopybooks();
            return "Processed successfully. Output: " + outputDir;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing copybooks: " + e.getMessage();
        }
    }
}