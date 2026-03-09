package com.springaidemo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "file_mapping")
public class FileMapping {

    @Id
    private String vsamFile;   // Primary key

    private String copybookFile;

    // Getters and setters
    public String getVsamFile() {
        return vsamFile;
    }

    public void setVsamFile(String vsamFile) {
        this.vsamFile = vsamFile;
    }

    public String getCopybookFile() {
        return copybookFile;
    }

    public void setCopybookFile(String copybookFile) {
        this.copybookFile = copybookFile;
    }
}