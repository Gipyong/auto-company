package com.autocompany.repository;
import com.autocompany.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {}
