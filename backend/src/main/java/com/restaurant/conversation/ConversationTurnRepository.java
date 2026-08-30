package com.restaurant.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, String> {
    @Query("SELECT t FROM ConversationTurn t WHERE t.sessionId = :sessionId ORDER BY t.createdAt ASC")
    List<ConversationTurn> findBySessionIdOrderByCreatedAt(String sessionId);

    Optional<ConversationTurn> findByIdempotencyKey(String idempotencyKey);

    @Query(value = "SELECT * FROM conversation_turns WHERE session_id = :sessionId ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<ConversationTurn> findRecentBySessionId(String sessionId, int limit);

    void deleteBySessionId(String sessionId);
}
