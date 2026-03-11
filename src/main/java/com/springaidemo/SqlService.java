package com.springaidemo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SqlService {

    @Autowired
    private FileMappingRepository repository;

    @Value("${app.data.csv}")
    private String dataDir;

    @Value("${app.copybook.intermediate}")
    private String copybookDir;

    @Value("${app.sql.output}")
    private String sqlOutputDir;

    private static final Logger logger = LoggerFactory.getLogger(SqlService.class);

	public SqlGenerationResult generateSqlForAllFiles() {
		SqlGenerationResult result = new SqlGenerationResult();
		
		Path dataPath = Path.of(dataDir);
		if (!Files.exists(dataPath) || !Files.isDirectory(dataPath)) {
			logger.warn("Data directory does not exist or is not a directory: {}", dataDir);
			return result;
		}

		File[] files = dataPath.toFile().listFiles();
		if (files == null) {
			logger.warn("No files found in data directory: {}", dataDir);
			return result;
		}

		int totalFiles = 0;
		for (File f : files) {
			if (f.isFile()) totalFiles++;
		}
		result.setTotalFilesInDataDir(totalFiles);

		for (File f : files) {
			if (!f.isFile()) continue;
			String fileName = f.getName();
			logger.info("Processing data file: {}", fileName);

			// Try searching with different extensions
			Optional<FileMapping> mappingOpt = Optional.empty();
			String fileNameForLookup = null;
			
			// Remove .csv extension if present
			String baseFileName = fileName;
			if (fileName.endsWith(".csv")) {
				baseFileName = fileName.substring(0, fileName.length() - 4);
			}
			
			// Try searching with different extensions: .txt, .TXT, .ps, .PS
			String[] extensionsToTry = {".txt", ".TXT", ".ps", ".PS"};
			for (String ext : extensionsToTry) {
				fileNameForLookup = baseFileName + ext;
				mappingOpt = repository.findByVsamFile(fileNameForLookup);
				if (mappingOpt.isPresent()) {
					logger.info("Found mapping for file {} with vsamFile: {}", fileName, fileNameForLookup);
					break;
				}
			}
			
			if (mappingOpt.isEmpty()) {
				String reason = "No FileMapping found in repository for any extension variant";
				logger.warn("No FileMapping found for file {}. Tried: {}.txt, {}.TXT, {}.ps, {}.PS. Skipping.", 
					fileName, baseFileName, baseFileName, baseFileName, baseFileName);
				result.addFailedFile(fileName, reason);
				continue;
			}

			String copybookFileName = mappingOpt.get().getCopybookFile();
			// Repository stores copybook name as .cpy, but intermediate file has .cpy.txt extension
			String copybookFileWithTxt = copybookFileName + ".txt";
			Path copybookPath = Path.of(copybookDir, copybookFileWithTxt);
			if (!Files.exists(copybookPath) || !Files.isRegularFile(copybookPath)) {
				String reason = "Copybook file not found: " + copybookPath;
				logger.warn("Copybook file not found for {}: {}. Skipping.", fileName, copybookPath);
				result.addFailedFile(fileName, reason);
				continue;
			}

			List<String> lines;
			try {
				lines = Files.readAllLines(copybookPath, StandardCharsets.UTF_8);
			} catch (IOException e) {
				String reason = "Failed to read copybook: " + e.getMessage();
				logger.error("Failed to read copybook file {}: {}", copybookPath, e.getMessage());
				result.addFailedFile(fileName, reason);
				continue;
			}

			if (lines.isEmpty()) {
				String reason = "Copybook file is empty";
				logger.warn("Copybook {} is empty. Skipping.", copybookPath);
				result.addFailedFile(fileName, reason);
				continue;
			}

			// Skip the header line - check if first column is "FieldName" (indicating header row)
			int startIndex = 0;
			String firstLine = lines.get(0).trim();
			String[] firstLineParts = firstLine.split(",", -1);
			if (firstLineParts.length > 0 && firstLineParts[0].trim().equalsIgnoreCase("FieldName")) {
				logger.debug("Skipping header line in copybook: {}", firstLine);
				startIndex = 1; // skip header
			}

			if (lines.size() <= startIndex) {
				String reason = "Copybook contains only header, no data fields";
				logger.warn("Copybook {} contains only header. Skipping.", copybookPath);
				result.addFailedFile(fileName, reason);
				continue;
			}

			List<String> fieldDefs = new ArrayList<>();
			int fillerCount = 0;
			for (int i = startIndex; i < lines.size(); i++) {
				String line = lines.get(i).trim();
				if (line.isEmpty()) continue;
				String[] parts = line.split(",", -1);
				if (parts.length < 4) {
					logger.warn("Skipping malformed copybook line (expected 4 cols): {}", line);
					continue;
				}
				String fldName = parts[0].trim();
				
				// EXPLICIT CHECK: Skip if field name is "FieldName" (this is the header row)
				if (fldName.equalsIgnoreCase("FieldName")) {
					logger.debug("Skipping FieldName header column at line {}", i);
					continue;
				}
				
				String sqlType = parts[1].trim();
				String precStr = parts[2].trim();
				String scaleStr = parts[3].trim();

				int precision = parseIntSafe(precStr, 0);
				int scale = parseIntSafe(scaleStr, 0);

				String sqlColumnName = sanitizeColumnName(fldName);
				// Handle FILLER fields - generate unique column names
				if (fldName.equalsIgnoreCase("FILLER")) {
					sqlColumnName = "filler_" + (++fillerCount);
				}
				
				String sqlTypeDef = mapToPostgresType(sqlType, precision, scale);

				fieldDefs.add(String.format("  %s %s", sqlColumnName, sqlTypeDef));
			}

			if (fieldDefs.isEmpty()) {
				String reason = "No usable fields generated from copybook";
				logger.warn("No fields generated for copybook {}. Skipping file {}.", copybookPath, fileName);
				result.addFailedFile(fileName, reason);
				continue;
			}

			String tableName = sanitizeTableName(fileName);
			StringBuilder sb = new StringBuilder();
			sb.append("CREATE TABLE ").append(tableName).append(" (\n");
			sb.append(String.join(",\n", fieldDefs));
			sb.append("\n);");

			// Replace .csv with .sql for output file name
			String outputFileName = fileName;
			if (fileName.endsWith(".csv")) {
				outputFileName = fileName.substring(0, fileName.length() - 4) + ".sql";
			}
			Path outPath = Path.of(sqlOutputDir, outputFileName);
			try {
				Files.createDirectories(outPath.getParent());
				Files.write(outPath, sb.toString().getBytes(StandardCharsets.UTF_8));
				logger.info("Wrote SQL file: {}", outPath);
				result.addSuccessfulFile(fileName);
			} catch (IOException e) {
				String reason = "Failed to write SQL file: " + e.getMessage();
				logger.error("Failed to write SQL file {}: {}", outPath, e.getMessage());
				result.addFailedFile(fileName, reason);
			}
		}
		return result;
	}

	private static int parseIntSafe(String s, int defaultVal) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return defaultVal;
		}
	}

	private static String mapToPostgresType(String sqlType, int precision, int scale) {
		// Copybook Precision/Scale meanings:
		// - NUMERIC/DECIMAL: precision = total digits, scale = decimal places
		// - CHAR: precision = character length, scale = always 0
		// - BIGINT/INTEGER: precision/scale represent field width in VSAM, ignored for SQL type
		//
		// Rules (SCALE FIRST):
		// 1. If scale > 0 -> ALWAYS numeric(precision, scale) regardless of type
		// 2. If scale == 0:
		//    - CHAR -> varchar(precision)
		//    - BIGINT -> bigint (ignore precision/scale)
		//    - INTEGER -> integer (ignore precision/scale)
		//    - NUMERIC/DECIMAL/NUMBER -> integer (if precision <= 9), bigint (if 9 < precision <= 18), else numeric(precision,0)
		//    - Default -> varchar(precision)

		if (sqlType == null) sqlType = "";
		String t = sqlType.trim().toUpperCase();

		// CHECK SCALE FIRST: If scale > 0, always use NUMERIC type
		if (scale > 0) {
			int p = Math.max(Math.max(precision, scale), 1);
			return String.format("numeric(%d,%d)", p, scale);
		}

		// Scale is 0 or less - now check the type
		// Handle BIGINT - use as-is, ignore precision/scale
		if (t.equals("BIGINT")) {
			return "bigint";
		}

		// Handle INTEGER - use as-is, ignore precision/scale
		if (t.equals("INTEGER")) {
			return "integer";
		}

		// Handle CHAR - use precision as varchar length
		if (t.equals("CHAR") || t.startsWith("CHAR(")) {
			int p = (precision > 0) ? precision : 255;
			return String.format("varchar(%d)", p);
		}

		// Handle NUMERIC/DECIMAL/NUMBER (with scale == 0)
		if (t.equals("NUMERIC") || t.equals("DECIMAL") || t.equals("NUMBER")) {
			// No decimal places -> choose best integer type
			if (precision > 0) {
				if (precision <= 9) return "integer"; // fits in 32-bit int
				if (precision <= 18) return "bigint"; // fits in 64-bit long
				// very large precision -> use numeric
				return String.format("numeric(%d,0)", precision);
			}
			return "numeric"; // no precision info
		}

		// Default fallback for unknown types
		int p = (precision > 0) ? precision : 255;
		return String.format("varchar(%d)", p);
	}

	private static String sanitizeColumnName(String name) {
		// replace non-alphanumeric with underscore and collapse multiple underscores
		String cleaned = name.replaceAll("[^A-Za-z0-9]", "_").replaceAll("_+", "_");
		if (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
		if (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length()-1);
		if (cleaned.isEmpty()) cleaned = "col";
		// If name starts with digit, prefix with c_ to make a valid identifier
		if (Character.isDigit(cleaned.charAt(0))) cleaned = "c_" + cleaned;
		return cleaned.toLowerCase();
	}

	private static String sanitizeTableName(String name) {
		// remove extension if present
		int dot = name.lastIndexOf('.');
		String base = (dot > 0) ? name.substring(0, dot) : name;
		String cleaned = base.replaceAll("[^A-Za-z0-9]", "_").toLowerCase();
		if (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
		if (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length()-1);
		if (cleaned.isEmpty()) cleaned = "table";
		if (Character.isDigit(cleaned.charAt(0))) cleaned = "t_" + cleaned;
		return cleaned;
	}

    
}