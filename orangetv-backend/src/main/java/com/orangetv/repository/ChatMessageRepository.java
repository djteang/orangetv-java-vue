package com.orangetv.repository;

import com.orangetv.entity.ChatMessage;
import com.orangetv.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationOrderByCreatedAtDesc(Conversation conversation);

    Page<ChatMessage> findByConversationOrderByCreatedAtDesc(Conversation conversation, Pageable pageable);

    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentMessages(@Param("conversationId") String conversationId, Pageable pageable);

    long countByConversationId(String conversationId);
}
