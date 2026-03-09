package com.springaidemo;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
}