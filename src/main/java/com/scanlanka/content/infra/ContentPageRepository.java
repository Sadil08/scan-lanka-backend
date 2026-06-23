package com.scanlanka.content.infra;

import com.scanlanka.content.domain.ContentPage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentPageRepository extends JpaRepository<ContentPage, String> {}
