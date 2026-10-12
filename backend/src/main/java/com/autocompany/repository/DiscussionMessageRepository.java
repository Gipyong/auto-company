package com.autocompany.repository;
import com.autocompany.entity.DiscussionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public interface DiscussionMessageRepository extends JpaRepository<DiscussionMessage, UUID> {}
