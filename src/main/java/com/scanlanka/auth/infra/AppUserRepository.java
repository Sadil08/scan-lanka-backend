package com.scanlanka.auth.infra;

import com.scanlanka.auth.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("select u from AppUser u where lower(u.email) = lower(?1)")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(?1)")
    boolean existsByEmailIgnoreCase(String email);
}
