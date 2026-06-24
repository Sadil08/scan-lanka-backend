package com.scanlanka.auth;

import com.scanlanka.auth.app.AdminProvisioner;
import com.scanlanka.auth.app.AuthProperties;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.Role;
import com.scanlanka.auth.infra.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Initial admin seeding from env (07 T-15). */
class AdminProvisionerTest {

    @Test
    void provisionsFirstAdminWhenConfiguredAndNoneExists() {
        AppUserRepository users = mock(AppUserRepository.class);
        var encoder = new BCryptPasswordEncoder(12);
        AuthProperties props = new AuthProperties(
            "secret-key-32-bytes-minimum-for-testing-x", "scanlanka", "scanlanka-web",
            15, 14, true, "sl_at", "sl_rt", 10, 5,
            "admin@scanlanka.lk", "AdminPass123!", true);
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(users.existsByEmailIgnoreCase("admin@scanlanka.lk")).thenReturn(false);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new AdminProvisioner(users, encoder, props).provisionIfNeeded();

        verify(users).save(any(AppUser.class));
    }

    @Test
    void skipsWhenAdminAlreadyExists() {
        AppUserRepository users = mock(AppUserRepository.class);
        var encoder = new BCryptPasswordEncoder(12);
        AuthProperties props = new AuthProperties(
            "secret-key-32-bytes-minimum-for-testing-x", "scanlanka", "scanlanka-web",
            15, 14, true, "sl_at", "sl_rt", 10, 5,
            "admin@scanlanka.lk", "AdminPass123!", true);
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        new AdminProvisioner(users, encoder, props).provisionIfNeeded();

        verify(users, never()).save(any());
    }

    @Test
    void skipsWhenEnvNotConfigured() {
        AppUserRepository users = mock(AppUserRepository.class);
        var encoder = new BCryptPasswordEncoder(12);
        AuthProperties props = new AuthProperties(
            "secret-key-32-bytes-minimum-for-testing-x", "scanlanka", "scanlanka-web",
            15, 14, true, "sl_at", "sl_rt", 10, 5, "", "", true);

        new AdminProvisioner(users, encoder, props).provisionIfNeeded();

        verifyNoInteractions(users);
    }
}
