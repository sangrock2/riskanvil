package com.sw103302.backend.repository;

import com.sw103302.backend.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    List<ChatConversation> findByUser_IdOrderByUpdatedAtDesc(Long userId);
    Optional<ChatConversation> findByIdAndUser_Id(Long id, Long userId);
}
