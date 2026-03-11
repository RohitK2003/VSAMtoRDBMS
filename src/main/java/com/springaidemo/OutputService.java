package com.springaidemo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OutputService {

    @Autowired
    private FileMappingRepository repository;

    @Value("${app.data.dir}")
    private String appDataDirPath;

    @Value("${app.copybook.dir}")
    private String copybookDirPath;
    
    @Value("${app.sql.output}")
    private String sqlOutputDir;

    @Value("${app.data.csv}")
    private String csvDataDirPath;

    @Value("${app.copybook.intermediate}")
    private String copybookIntermediateDirPath;

    private Path appDataDir;
    private Path copybookDir;
    private Path csvDataDir;
    private Path copybookIntermediateDir;

    @Autowired
    public void init() throws IOException {
        appDataDir = Paths.get(appDataDirPath);
        copybookDir = Paths.get(copybookDirPath);
        csvDataDir = Paths.get(csvDataDirPath);
        copybookIntermediateDir = Paths.get(copybookIntermediateDirPath);
        

        Files.createDirectories(appDataDir);
        Files.createDirectories(copybookDir);
        Files.createDirectories(csvDataDir);
        Files.createDirectories(copybookIntermediateDir);
    }

    /**
     * Import CSV mapping VSAM → Copybook
     */
    public void importCsv(MultipartFile file) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("Uploaded file is empty");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                String vsamFile = parts[0].trim();
                String copybookFile = parts[1].trim();

                if (!vsamFile.isEmpty() && !copybookFile.isEmpty()) {
                    FileMapping mapping = new FileMapping();
                    mapping.setVsamFile(vsamFile);
                    mapping.setCopybookFile(copybookFile);
                    repository.save(mapping);
                }
            }
        }
    }

    /**
     * Unzip uploaded data directly into app-data folder
     */
    public Path unzipData(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Uploaded file is empty");

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = appDataDir.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        System.out.println("Zip extracted to: " + appDataDir.toAbsolutePath());
        return appDataDir;
    }

    /**
     * Process all .ps or .txt data files and convert to CSV
     */
    public void processDataFiles() throws Exception {
        if (!Files.exists(appDataDir)) {
            throw new RuntimeException("Data directory not found: " + appDataDir.toAbsolutePath());
        }

        Files.createDirectories(csvDataDir);

        try (Stream<Path> files = Files.list(appDataDir)) {
            files.filter(file -> file.toString().toLowerCase().endsWith(".ps") ||
                                 file.toString().toLowerCase().endsWith(".txt"))
                 .forEach(file -> {
                     try {
                         processSingleFile(file, csvDataDir);
                     } catch (Exception e) {
                         throw new RuntimeException(e);
                     }
                 });
        }
    }

    /**
     * Process a single readable data file based on its copybook layout
     */
    private void processSingleFile(Path dataFile, Path convertedDir) throws Exception {
        String fileName = dataFile.getFileName().toString();
        FileMapping mapping = repository.findByVsamFile(fileName)
                .orElseThrow(() -> new RuntimeException("No copybook mapping found for: " + fileName));

        String copybookName = mapping.getCopybookFile();
        Path layoutFile = copybookIntermediateDir.resolve(copybookName + ".txt"); // processed layout from intermediate dir

        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        Path csvOutputFile = convertedDir.resolve(baseName + ".csv");

        convertToCsv(dataFile, layoutFile, csvOutputFile);
    }

    private void convertToCsv(Path dataFile, Path layoutFile, Path csvOutputFile) throws Exception {

        class Field {
            String name, type;
            int precision, scale;
            Field(String n, String t, int p, int s) { name = n; type = t; precision = p; scale = s; }
        }

        // 1️⃣ Read layout file and build fields
        List<Field> fields = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(layoutFile, StandardCharsets.ISO_8859_1)) {
            String line;
            boolean skipFirstLine = true; // skip "Copybook:" line
            boolean skipHeader = true;    // skip header "FieldName,SQLType,Precision,Scale"

            while ((line = br.readLine()) != null) {
                if (skipFirstLine) { skipFirstLine = false; continue; }
                if (skipHeader) { skipHeader = false; continue; }

                String[] parts = line.split(",");
                if (parts.length < 4) continue;

                String name = parts[0].trim();
                String type = parts[1].trim();
                int precision = Integer.parseInt(parts[2].trim());
                int scale = Integer.parseInt(parts[3].trim());

                fields.add(new Field(name, type, precision, scale));
            }
        }

        // Calculate total record length from field definitions
        int recordLength = fields.stream().mapToInt(f -> f.precision).sum();
        
        // 2️⃣ Write CSV header and read data file in fixed-record chunks
        try (BufferedWriter writer = Files.newBufferedWriter(csvOutputFile, StandardCharsets.UTF_8)) {

            // Write header line
            String header = fields.stream().map(f -> f.name).reduce((a,b) -> a + "," + b).orElse("");
            writer.write(header);
            writer.newLine();

            // Read data file as binary and process fixed-length records
            byte[] fileBytes = Files.readAllBytes(dataFile);
            String fileContent = new String(fileBytes, StandardCharsets.ISO_8859_1);
            
            // Detect if file has line separators (from EBCDIC conversion)
            boolean hasLineBreaks = fileContent.contains("\n") || fileContent.contains("\r");
            
            List<String> recordLines = new ArrayList<>();
            
            if (hasLineBreaks) {
                // File has embedded newlines - use line-by-line reading
                try (BufferedReader reader = new BufferedReader(new java.io.StringReader(fileContent))) {
                    String recordLine;
                    while ((recordLine = reader.readLine()) != null) {
                        if (!recordLine.trim().isEmpty()) {
                            recordLines.add(recordLine);
                        }
                    }
                }
            } else {
                // Pure fixed-length format without newlines - split into fixed-size chunks
                int offset = 0;
                while (offset < fileContent.length()) {
                    int endPos = Math.min(offset + recordLength, fileContent.length());
                    String recordLine = fileContent.substring(offset, endPos);
                    offset = endPos;
                    
                    if (!recordLine.trim().isEmpty()) {
                        recordLines.add(recordLine);
                    }
                }
            }

            // Process all extracted records
            for (String recordLine : recordLines) {
                StringBuilder sb = new StringBuilder();
                int pos = 0;
                boolean recordEnded = false;

                for (int i = 0; i < fields.size(); i++) {
                    Field f = fields.get(i);
                    String value = "";
                    
                    // Check if this is the last field
                    boolean isLastField = (i == fields.size() - 1);
                    boolean isFillerField = f.name.toUpperCase().contains("FILLER");

                    // If we already hit Filler and processed it, skip remaining fields
                    if (recordEnded) {
                        value = "";
                    } else if (pos >= recordLine.length()) {
                        value = "";
                    } else {
                        int remaining = recordLine.length() - pos;
                        
                        // Special handling for Filler fields ONLY if it's the LAST field
                        if (isFillerField && isLastField) {
                            // Skip all trailing spaces in Filler field (last field only)
                            int spacesToSkip = 0;
                            while (pos + spacesToSkip < recordLine.length() && 
                                   recordLine.charAt(pos + spacesToSkip) == ' ') {
                                spacesToSkip++;
                            }
                            // Leave Filler blank
                            value = "";
                            pos += spacesToSkip;
                            // Mark that Filler is done - next non-space is start of next record
                            recordEnded = true;
                        } else {
                            // Normal field extraction (for non-last Filler or any other field)
                            int len = Math.min(f.precision, remaining);
                            value = recordLine.substring(pos, pos + len);
                            pos += len;

                            // For scaled numeric, continue reading until '{' or scale
                            if ((f.type.equalsIgnoreCase("NUMERIC") || f.type.equalsIgnoreCase("BIGINT")) && f.scale > 0) {
                                StringBuilder decimalPart = new StringBuilder();
                                int count = 0;
                                while (pos < recordLine.length() && recordLine.charAt(pos) != '{' && count < f.scale) {
                                    decimalPart.append(recordLine.charAt(pos));
                                    pos++; count++;
                                }
                                value = value + "." + (decimalPart.length() > 0 ? decimalPart.toString() : "0");
                            }

                            // Skip '{' if present
                            if (pos < recordLine.length() && recordLine.charAt(pos) == '{') pos++;
                        }
                    }

                    // Remove any { leftover just in case
                    value = value.replace("{", "").trim();
                    sb.append(value);

                    if (i < fields.size() - 1) sb.append(",");
                }
                writer.write(sb.toString());
                writer.newLine();
            }
        }
        System.out.println("CSV created: " + csvOutputFile.toAbsolutePath());
    }
}