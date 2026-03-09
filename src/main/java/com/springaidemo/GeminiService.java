package com.springaidemo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class GeminiService {

    private final ChatClient chatClient;
    private final FileMappingRepository repository;

    @Value("${app.output.dir}")
    private String dataDir;

    @Value("${app.copybook.dir}")
    private String copybookDir;

    @Value("${app.sql.output}")
    private String sqlOutputDir;

    public GeminiService(ChatClient.Builder builder,
                         FileMappingRepository repository) {
        this.chatClient = builder.build();
        this.repository = repository;
    }

    /**
     * Process all CSV files in the data directory,
     * generate DDL via Gemini, and write SQL files.
     */
    public void generateSqlForAllFiles() {
        try {

            Files.createDirectories(Path.of(sqlOutputDir));

            File folder = new File(dataDir);
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));

            if (files == null || files.length == 0) {
                System.out.println("No CSV files found in folder: " + dataDir);
                return;
            }

            for (File csv : files) {
                String fileName = csv.getName();
                System.out.println("\nProcessing CSV file: " + fileName);

                // Correct repository method
                String vsamFileName = fileName.replace(".csv", ".txt");
                Optional<FileMapping> mapping = repository.findByVsamFile(vsamFileName);

                if (mapping.isEmpty()) {
                    System.out.println("Mapping not found for CSV file: " + fileName);
                    continue;
                }

                String copybookName = mapping.get().getCopybookFile();
                Path copybookPath = Path.of(copybookDir, copybookName + ".txt");

                if (!Files.exists(copybookPath)) {
                    System.out.println("Copybook file not found: " + copybookPath);
                    continue;
                }

                System.out.println("Found copybook: " + copybookName);

                String copybookContent = Files.readString(copybookPath);
                if (copybookContent.isEmpty()) {
                    System.out.println("Copybook file is empty: " + copybookName);
                    continue;
                }

                // Generate DDL using Gemini LLM
                String ddl = generateDDL(fileName, copybookContent);

                if (ddl.isEmpty()) {
                    System.out.println("Gemini returned empty DDL for file: " + fileName);
                    continue;
                }

                String sqlFileName = fileName.replace(".csv", ".sql");
                Path sqlPath = Path.of(sqlOutputDir, sqlFileName);

                // Write SQL file
                Files.writeString(sqlPath, ddl);
                System.out.println("SQL generated: " + sqlPath);
            }

        } catch (Exception e) {
            System.out.println("Error generating SQL files: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error generating SQL files: " + e.getMessage(), e);
        }
    }

    /**
     * Generate CREATE TABLE DDL using Gemini LLM
     */
    private String generateDDL(String fileName, String copybookContent) {
        String tableName = fileName.replace(".csv", "");

        String prompt = """
You are a SQL generation assistant.

Generate a SQL CREATE TABLE statement.

Table Name:
%s

The copybook file contains fields in CSV format with columns:

FieldName,SQLType,Precision,Scale

Follow these rules strictly:

1. If SQLType = CHAR convert to VARCHAR(Precision)
2. If SQLType = NUMERIC and Scale > 0 convert to NUMERIC(Precision, Scale)
3. If SQLType = NUMERIC and Scale = 0 convert to NUMERIC(Precision)
4. If SQLType = BIGINT keep BIGINT
5. Ignore any field where FieldName = FILLER
6. Preserve field names exactly
7. Only use fields in the copybook
8. Do NOT create additional columns
9. Do NOT modify column names
10. Return ONLY the SQL CREATE TABLE statement
11. Do not include markdown formatting

Copybook Data:
%s
""".formatted(tableName, copybookContent);

        String response = "";

        try {
            response = chatClient
                    .prompt(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            System.out.println("Gemini API error for file: " + fileName);
            e.printStackTrace();
        }

        return cleanSql(response);
    }

    /**
     * Remove any markdown formatting from LLM output
     */
    private String cleanSql(String sql) {
        if (sql == null) return "";
        return sql.replace("```sql", "")
                  .replace("```", "")
                  .trim();
    }
}