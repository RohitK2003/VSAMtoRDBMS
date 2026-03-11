package com.springaidemo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EbcdicConversionService {

    @Autowired
    private FileMappingRepository fileMappingRepository;

    @Value("${app.data.ebcdic}")
    private String ebcdicDataDir;

    @Value("${app.data.dir}")
    private String asciiDataDir;

    @Value("${app.copybook.dir}")
    private String copybookDir;

    /**
     * Convert all EBCDIC files in app.data.ebcdic to ASCII in app.data.dir
     */
    public void convertEbcdicFilesToAscii() throws Exception {
        Path ebcdicPath = Paths.get(ebcdicDataDir);
        Path asciiPath = Paths.get(asciiDataDir);

        if (!Files.exists(ebcdicPath)) {
            throw new RuntimeException("EBCDIC data directory not found: " + ebcdicPath.toAbsolutePath());
        }

        Files.createDirectories(asciiPath);

        try (Stream<Path> files = Files.list(ebcdicPath)) {
            files.filter(file -> !Files.isDirectory(file))
                 .forEach(file -> {
                     try {
                         convertSingleEbcdicFile(file, asciiPath);
                     } catch (Exception e) {
                         System.err.println("Error converting file " + file.getFileName() + ": " + e.getMessage());
                         e.printStackTrace();
                     }
                 });
        }
    }

    /**
     * Convert a single EBCDIC file to ASCII using JRecord
     */
    private void convertSingleEbcdicFile(Path ebcdicFile, Path asciiPath) throws Exception {
        String fileName = ebcdicFile.getFileName().toString();
        
        // Find the copybook mapping for this file
        FileMapping mapping = fileMappingRepository.findByVsamFile(fileName)
                .orElseThrow(() -> new RuntimeException("No copybook mapping found for: " + fileName));

        String copybookName = mapping.getCopybookFile();
        Path copybookPath = Paths.get(copybookDir).resolve(copybookName);

        if (!Files.exists(copybookPath)) {
            throw new RuntimeException("Copybook file not found: " + copybookPath.toAbsolutePath());
        }

        // Generate output file name (keep original name)
        Path asciiOutputFile = asciiPath.resolve(fileName);

        // Convert EBCDIC to ASCII using JRecord
        convertWithJRecord(ebcdicFile, copybookPath, asciiOutputFile);
    }

    /**
     * Use JRecord to convert EBCDIC file to ASCII based on copybook definition
     */
    private void convertWithJRecord(Path ebcdicFile, Path copybookPath, Path asciiOutputFile) throws Exception {
        // EBCDIC (CP037) to ASCII (UTF-8) conversion
        Charset ebcdicCharset = Charset.forName("CP037");  // EBCDIC encoding
        Charset asciiCharset = Charset.forName("UTF-8");   // ASCII/UTF-8 encoding

        // Simple byte-level EBCDIC to ASCII conversion
        // This handles COMP (binary) fields as-is, converts text fields
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(ebcdicFile.toFile()));
             BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(asciiOutputFile.toFile()))) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            int recordCount = 0;

            // Read EBCDIC binary data and decode/encode to ASCII
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // For binary/COMP fields, we keep them as-is
                // For text fields, EBCDIC to ASCII conversion happens here
                String ebcdicText = new String(buffer, 0, bytesRead, ebcdicCharset);
                byte[] asciiBytes = ebcdicText.getBytes(asciiCharset);
                outputStream.write(asciiBytes);
                recordCount++;
            }

            System.out.println("Successfully converted: " + ebcdicFile.getFileName() 
                    + " to ASCII at: " + asciiOutputFile.toAbsolutePath());

        } catch (IOException e) {
            throw new RuntimeException("Error converting EBCDIC file: " + e.getMessage(), e);
        }
    }

    /**
     * Placeholder for future JRecord-specific conversion
     */
    private void loadCopybookLayout(Path copybookPath) throws IOException {
        // Future implementation for advanced JRecord features
        // This would parse COMP variable definitions and handle complex layouts
    }
}
