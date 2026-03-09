package com.springaidemo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileMappingRepository extends JpaRepository<FileMapping, String>{

	Optional<FileMapping> findByVsamFile(String fileName);

}
