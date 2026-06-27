package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.PostalZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostalZoneRepository extends JpaRepository<PostalZone, String> {
}
