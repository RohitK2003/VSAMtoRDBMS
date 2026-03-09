package com.springaidemo;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import net.sf.JRecord.Common.FieldDetail;
import net.sf.JRecord.Details.LayoutDetail;
import net.sf.JRecord.Details.RecordDetail;
import net.sf.JRecord.External.CobolCopybookLoader;
import net.sf.JRecord.External.ExternalRecord;

@Service
public class CopybookService {

    @Value("${app.copybook.dir}")
    private String copybookDir;

    @Value("${app.output.dir}")
    private String outputDir;

    // Regex to parse PIC clause
    private static final Pattern PIC_PATTERN =
            Pattern.compile("PIC\\s+([SX9Vv0-9\\(\\)]+)(\\s+COMP-3|\\s+COMP|\\s+SIGNED)?",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern OCCURS_PATTERN =
            Pattern.compile("OCCURS\\s+(\\d+)\\s+TIMES", Pattern.CASE_INSENSITIVE);

    public Path unzipCopybooks(MultipartFile file) throws Exception {

        if (file.isEmpty())
            throw new IllegalArgumentException("Uploaded file is empty");

        Path dir = Path.of(copybookDir);
        Files.createDirectories(dir);

        return ZipUtil.unzip(file, dir);
    }

    public Path processCopybooks() throws Exception {
        Path dir = Path.of(copybookDir);  // folder from application.properties
        Files.createDirectories(dir);     // ensure folder exists

        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> copybooks = paths
                    .filter(p -> p.toString().toLowerCase().endsWith(".cpy"))
                    .collect(Collectors.toList());
            for (Path copybook : copybooks) {
                // Always write processed file to the root copybookDir, not subfolders
                processSingleCopybook(copybook, dir);
            }
        }
        return dir; // processed files are now directly in copybookDir
    }

    private void processSingleCopybook(Path copybookPath, Path outputDir) {
        try {
            List<String> lines = Files.readAllLines(copybookPath);
            CobolCopybookLoader loader = new CobolCopybookLoader();
            ExternalRecord externalRecord = loader.loadCopyBook(
                    copybookPath.toString(),
                    0,
                    0,
                    "",
                    0,
                    0,
                    null
            );

            LayoutDetail layout = externalRecord.asLayoutDetail();
            RecordDetail record = layout.getRecord(0);

            File outFile = outputDir
                    .resolve(copybookPath.getFileName().toString() + ".txt")
                    .toFile();

            try (PrintWriter writer = new PrintWriter(outFile)) {

                writer.println("Copybook: " + copybookPath.getFileName());
                writer.println("FieldName,SQLType,Precision,Scale");

                Map<String, String> picMap = buildPicMap(lines);

                for (int i = 0; i < record.getFieldCount(); i++) {

                    FieldDetail field = record.getField(i);
                    String name = field.getName();

                    if (isCharField(field)) {
                        int len = field.getLen();
                        writer.println(name + ",CHAR," + len + ",0");
                    } else {

                        String picClause = picMap.getOrDefault(name, "");
                        SqlField sqlField = parsePicClauseNumeric(picClause);

                        writer.println(name + ","
                                + sqlField.sqlType + ","
                                + sqlField.precision + ","
                                + sqlField.scale);
                    }
                }
            }
            System.out.println("Processed: " + copybookPath.getFileName());
        } catch (Exception e) {
            System.err.println("Error processing " + copybookPath.getFileName());
            e.printStackTrace();
        }
    }

    /** Build map: fieldName → PIC clause */
    private Map<String, String> buildPicMap(List<String> lines) {

        Map<String, String> picMap = new HashMap<>();
        String currentOccurs = null;
        int occursCount = 1;
        for (String line : lines) {
            line = line.trim().toUpperCase();
            if (line.isEmpty())
                continue;
            Matcher occursM = OCCURS_PATTERN.matcher(line);
            if (occursM.find()) {
                currentOccurs = line.split("\\s+")[1];
                occursCount = Integer.parseInt(occursM.group(1));
                continue;
            }

            Matcher picM = PIC_PATTERN.matcher(line);

            if (picM.find()) {
                String fieldName = extractFieldName(line);
                String picClause =
                        picM.group(1)
                                + (picM.group(2) != null ? picM.group(2) : "");
                if (currentOccurs != null && occursCount > 1) {
                    for (int i = 1; i <= occursCount; i++) {
                        picMap.put(fieldName + "_" + i, picClause);
                    }
                    currentOccurs = null;
                    occursCount = 1;
                } else {
                    picMap.put(fieldName, picClause);
                }
            }
        }
        return picMap;
    }

    /** Extract field name */
    private String extractFieldName(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2)
            return parts[1].replace(".", "");
        return "";
    }

    /** Detect CHAR field */
    private boolean isCharField(FieldDetail field) {
        int baseType = field.getType() & 0x7F;
        return baseType == 0;
    }

    /** Parse numeric PIC clause */
    private SqlField parsePicClauseNumeric(String pic) {
        if (pic == null || pic.isEmpty())
            return new SqlField("NUMERIC", 0, 0);
        pic = pic.replaceAll("\\s+", "");
        int precision = extractNumericLength(pic);
        int scale = pic.contains("V") ? extractNumericScale(pic) : 0;
        if (pic.contains("COMP-3")) {
            return new SqlField("NUMERIC", precision, scale);
        } else if (pic.contains("COMP")) {
            if (precision <= 4)
                return new SqlField("INTEGER", precision, scale);
            return new SqlField("BIGINT", precision, scale);
        } else {
            return new SqlField("NUMERIC", precision, scale);
        }
    }

    /** Extract numeric length */
    private int extractNumericLength(String pic) {
        Matcher m = Pattern.compile("\\((\\d+)\\)").matcher(pic);
        if (m.find())
            return Integer.parseInt(m.group(1));
        int count = 0;
        for (char c : pic.toCharArray())
            if (c == '9')
                count++;
        return Math.max(1, count);
    }

    /** Extract decimal scale */
    private int extractNumericScale(String pic) {
        int idx = pic.indexOf('V');
        if (idx >= 0) {
            String dec = pic.substring(idx + 1)
                    .replaceAll("[^0-9]", "");
            return dec.length();
        }
        return 0;
    }

    /** SQL field holder */
    static class SqlField {
        String sqlType;
        int precision;
        int scale;
        SqlField(String sqlType, int precision, int scale) {
            this.sqlType = sqlType;
            this.precision = precision;
            this.scale = scale;
        }
    }
}