package com.autotag.docs.repository;

import com.autotag.docs.entity.Document;
import com.autotag.docs.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOrderByCreatedAtDesc();

    List<Document> findByStatusOrderByCreatedAtDesc(DocumentStatus status);

    @Query("SELECT DISTINCT d FROM Document d JOIN d.tags t WHERE LOWER(t.tagName) = LOWER(:tagName) ORDER BY d.createdAt DESC")
    List<Document> findByTagName(@Param("tagName") String tagName);

    @Query("SELECT DISTINCT t.tagName FROM DocumentTag t ORDER BY t.tagName ASC")
    List<String> findAllDistinctTagNames();

    @Query("SELECT d FROM Document d WHERE d.id IN :ids")
    List<Document> findAllByIds(@Param("ids") List<UUID> ids);
}
