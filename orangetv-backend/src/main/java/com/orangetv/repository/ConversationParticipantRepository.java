package com.orangetv.repository;

import com.orangetv.entity.ConversationParticipant;
import com.orangetv.entity.ConversationParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, ConversationParticipantId> {

    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId")
    List<ConversationParticipant> findByConversationId(@Param("conversationId") String conversationId);

    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.user.id = :userId")
    List<ConversationParticipant> findByUserId(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(cp) > 0 THEN true ELSE false END FROM ConversationParticipant cp " +
           "WHERE cp.conversation.id = :conversationId AND cp.user.id = :userId")
    boolean existsByConversationIdAndUserId(@Param("conversationId") String conversationId, @Param("userId") Long userId);
}
