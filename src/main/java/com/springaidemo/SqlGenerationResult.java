package com.springaidemo;

import java.util.ArrayList;
import java.util.List;

public class SqlGenerationResult {
    private int totalFilesInDataDir;
    private int successfulGenerations;
    private int failedGenerations;
    private List<String> successfulFiles;
    private List<FailureDetail> failedFiles;

    public SqlGenerationResult() {
        this.totalFilesInDataDir = 0;
        this.successfulGenerations = 0;
        this.failedGenerations = 0;
        this.successfulFiles = new ArrayList<>();
        this.failedFiles = new ArrayList<>();
    }

    public static class FailureDetail {
        private String fileName;
        private String reason;

        public FailureDetail(String fileName, String reason) {
            this.fileName = fileName;
            this.reason = reason;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    // Getters and setters
    public int getTotalFilesInDataDir() {
        return totalFilesInDataDir;
    }

    public void setTotalFilesInDataDir(int totalFilesInDataDir) {
        this.totalFilesInDataDir = totalFilesInDataDir;
    }

    public int getSuccessfulGenerations() {
        return successfulGenerations;
    }

    public void setSuccessfulGenerations(int successfulGenerations) {
        this.successfulGenerations = successfulGenerations;
    }

    public int getFailedGenerations() {
        return failedGenerations;
    }

    public void setFailedGenerations(int failedGenerations) {
        this.failedGenerations = failedGenerations;
    }

    public List<String> getSuccessfulFiles() {
        return successfulFiles;
    }

    public void setSuccessfulFiles(List<String> successfulFiles) {
        this.successfulFiles = successfulFiles;
    }

    public List<FailureDetail> getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(List<FailureDetail> failedFiles) {
        this.failedFiles = failedFiles;
    }

    public void addSuccessfulFile(String fileName) {
        this.successfulFiles.add(fileName);
        this.successfulGenerations++;
    }

    public void addFailedFile(String fileName, String reason) {
        this.failedFiles.add(new FailureDetail(fileName, reason));
        this.failedGenerations++;
    }

    @Override
    public String toString() {
        return "SqlGenerationResult{" +
                "totalFilesInDataDir=" + totalFilesInDataDir +
                ", successfulGenerations=" + successfulGenerations +
                ", failedGenerations=" + failedGenerations +
                ", successfulFiles=" + successfulFiles +
                ", failedFiles=" + failedFiles +
                '}';
    }
}
