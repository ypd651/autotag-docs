package com.autotag.docs.repository;

import com.autotag.docs.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {
    List<DocumentTag> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
