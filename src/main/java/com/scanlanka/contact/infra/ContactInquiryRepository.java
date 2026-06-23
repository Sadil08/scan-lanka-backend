package com.scanlanka.contact.infra;

import com.scanlanka.contact.domain.ContactInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {
    List<ContactInquiry> findByStatusOrderByCreatedAtDesc(String status);
}
