package com.springaidemo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/files")
public class FileController {

    @Autowired
    private OutputService outputService;
    @Autowired
    private SqlService sqlGenerationService;
    @Autowired
    private EbcdicConversionService ebcdicConversionService;
    @Autowired
    private FileMappingRepository fileMappingRepository;

    @Value("${app.sql.output}")
    private String sqlOutputDir;

    @Value("${app.output.dir}")
    private String csvOutputDir;

    @Value("${app.data.ebcdic}")
    private String ebcdicDataDir;

    @Value("${app.base.location}")
    private String baseLocation;

    @Value("${app.template.location}")
    private String templateLocation;

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

    @PostMapping("/upload-ebcdic-data")
    public ResponseEntity<String> uploadEbcdicDataZip(@RequestParam("file") MultipartFile file) {
        try {
            Path extractedDir = ZipUtil.unzip(file, Paths.get(ebcdicDataDir));
            return ResponseEntity.ok("Zip extracted successfully at: " + extractedDir.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error extracting Zip: " + e.getMessage());
        }
    }

    @GetMapping("/process-data")
    public ResponseEntity<String> processData() {
        try {
            outputService.processDataFiles();
            return ResponseEntity.ok("Data files processed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing data files: " + e.getMessage());
        }
    }

    @GetMapping("/ebcdic-to-ascii")
    public ResponseEntity<String> convertEbcdicToAscii() {
        try {
            ebcdicConversionService.convertEbcdicFilesToAscii();
            return ResponseEntity.ok("EBCDIC files converted to ASCII successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error converting EBCDIC to ASCII: " + e.getMessage());
        }
    }
    @GetMapping("/generate")
    public ResponseEntity<SqlGenerationResult> generateSqlFiles() {

        try {

            SqlGenerationResult result = sqlGenerationService.generateSqlForAllFiles();

            return ResponseEntity.ok(result);

        } catch (Exception e) {

            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download-output")
    public ResponseEntity<Resource> downloadOutput() {
        try {
            // Create temp directory for the output zip
            Path tempDir = Files.createTempDirectory("output_");
            Path outputZipPath = tempDir.resolve("output.zip");

            // Get SQL and CSV directories
            Path sqlDir = Paths.get(sqlOutputDir);
            Path csvDir = Paths.get(csvOutputDir);

            // Create zip with sql-output and csv-data folders
            ZipUtil.createZipFromDirectories(outputZipPath, sqlDir, "sql-output", csvDir, "csv-data");

            // Load the zip file as a resource
            Resource resource = new FileSystemResource(outputZipPath);

            // Return the file with appropriate headers
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=output.zip")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Clean all files and folders at the base location and clear FileMapping table
     */
    private void cleanBaseLocation() throws Exception {
        Path basePath = Paths.get(baseLocation);
        
        if (!Files.exists(basePath)) {
            System.out.println("Base location does not exist: " + basePath.toAbsolutePath());
        } else {
            try (java.util.stream.Stream<Path> paths = Files.walk(basePath)) {
                paths.sorted((p1, p2) -> Integer.compare(p2.getNameCount(), p1.getNameCount()))
                     .forEach(path -> {
                         try {
                             if (!path.equals(basePath)) {
                                 if (Files.isDirectory(path)) {
                                     Files.deleteIfExists(path);
                                 } else {
                                     Files.deleteIfExists(path);
                                 }
                             }
                         } catch (Exception e) {
                             System.err.println("Error deleting: " + path.toAbsolutePath() + " - " + e.getMessage());
                         }
                     });
            }
            
            System.out.println("Base location cleaned: " + basePath.toAbsolutePath());
        }
        
        // Clear FileMapping table
        try {
            fileMappingRepository.deleteAll();
            System.out.println("FileMapping table cleared successfully!");
        } catch (Exception e) {
            System.err.println("Error clearing FileMapping table: " + e.getMessage());
            throw e;
        }
    }
    
    @GetMapping("/download-template")
    public ResponseEntity<Resource> downloadTemplate() {
        try {
            Path templateFilePath = Paths.get(templateLocation).resolve("VsamCopybookMapping.csv");
            
            if (!Files.exists(templateFilePath)) {
                return ResponseEntity.status(404).body(null);
            }
            
            Resource resource = new FileSystemResource(templateFilePath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=VsamCopybookMapping.csv")
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }
    
    @DeleteMapping("/end-session")
    public ResponseEntity<String> endSession() {
        try {
            cleanBaseLocation();
            return ResponseEntity.ok("Session Ended! All files and folders at base location have been cleaned.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error cleaning base location: " + e.getMessage());
        }
    }
}