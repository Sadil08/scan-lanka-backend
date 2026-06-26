package com.scanlanka.support.infra;

import com.scanlanka.support.domain.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {

    Optional<SupportConversation> findByAccessTokenHash(String accessTokenHash);

    List<SupportConversation> findByStatusOrderByUpdatedAtDesc(String status);
}
