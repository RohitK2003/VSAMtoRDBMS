package com.springaidemo;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.web.multipart.MultipartFile;

public class ZipUtil {

    // OLD METHOD (unchanged)
    public static Path unzipToTemp(MultipartFile file) throws IOException {

        Path tempDir = Files.createTempDirectory("copybooks_");

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                Path filePath = tempDir.resolve(entry.getName());

                if (!entry.isDirectory()) {

                    Files.createDirectories(filePath.getParent());

                    Files.copy(
                            zis,
                            filePath,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }
        }

        System.out.println("Zip extracted to: " + tempDir.toAbsolutePath());

        return tempDir;
    }

    // NEW METHOD (for application.properties directory)
    public static Path unzip(MultipartFile file, Path destDir) throws IOException {

        Files.createDirectories(destDir);

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                Path filePath = destDir.resolve(entry.getName());

                if (!entry.isDirectory()) {

                    Files.createDirectories(filePath.getParent());

                    Files.copy(
                            zis,
                            filePath,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }
        }

        System.out.println("Zip extracted to: " + destDir.toAbsolutePath());

        return destDir;
    }

    // NEW METHOD: Create a zip file from multiple directories
    public static Path createZipFromDirectories(Path outputZipPath,
                                                 Path sqlDir, String sqlFolderName,
                                                 Path csvDir, String csvFolderName) throws IOException {

        // Create parent directory if it doesn't exist
        Files.createDirectories(outputZipPath.getParent());

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZipPath))) {

            // Add SQL files from sqlDir
            if (Files.exists(sqlDir) && Files.isDirectory(sqlDir)) {
                addDirToZip(zos, sqlDir, sqlFolderName);
            }

            // Add CSV files from csvDir
            if (Files.exists(csvDir) && Files.isDirectory(csvDir)) {
                addDirToZip(zos, csvDir, csvFolderName);
            }
        }

        return outputZipPath;
    }

    // Helper method to add a directory and its contents to a zip
    private static void addDirToZip(ZipOutputStream zos, Path dir, String zipDirName) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                String entryName = zipDirName + "/" + path.getFileName().toString();

                if (Files.isDirectory(path)) {
                    // Recursively add subdirectories
                    addDirToZip(zos, path, entryName);
                } else {
                    // Add file to zip
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }
}