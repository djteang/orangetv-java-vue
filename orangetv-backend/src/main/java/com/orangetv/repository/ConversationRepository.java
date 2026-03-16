package com.orangetv.repository;

import com.orangetv.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    @Query("SELECT c FROM Conversation c JOIN c.participants p WHERE p.user.id = :userId ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE c.type = 'private' AND p1.user.id = :userId1 AND p2.user.id = :userId2")
    Optional<Conversation> findPrivateConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.participants WHERE c.id = :id")
    Optional<Conversation> findByIdWithParticipants(@Param("id") String id);
}
