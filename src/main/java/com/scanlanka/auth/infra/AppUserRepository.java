package com.scanlanka.auth.infra;

import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("select u from AppUser u where lower(u.email) = lower(?1)")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(?1)")
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    @Query("SELECT u.id FROM AppUser u WHERE u.role = :role AND u.status = com.scanlanka.auth.domain.UserStatus.ACTIVE")
    List<Long> findActiveCustomerIds(Role role);
}
