package com.scanlanka.auth;

import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.Role;
import com.scanlanka.auth.infra.AppUserRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared auth helpers for integration tests (07-auth). */
public final class AuthTestSupport {

    private AuthTestSupport() {}

    public static AppUser seedAdmin(AppUserRepository users, PasswordEncoder encoder, String email) {
        AppUser admin = new AppUser(email, encoder.encode("password123"), "Admin", Role.ADMIN);
        admin.setEmailVerified(true);
        admin.setTotpEnabled(true);
        admin.setTotpSecret("JBSWY3DPEHPK3PXP");
        return users.save(admin);
    }

    public static String currentTotp(String secret) {
        try {
            long counter = new SystemTimeProvider().getTime() / 30;
            return new DefaultCodeGenerator().generate(secret, counter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Cookie loginAdmin(MockMvc mvc, String email, String totpSecret) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"totp\":\""
                    + currentTotp(totpSecret) + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return res.getResponse().getCookie("sl_at");
    }

    public static void verifyCustomer(AppUserRepository users, String email) {
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            u.setEmailVerified(true);
            users.save(u);
        });
    }

    public static Cookie loginVerifiedCustomer(MockMvc mvc, AppUserRepository users, String email)
            throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"U\"}"));
        verifyCustomer(users, email);
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk()).andReturn();
        return res.getResponse().getCookie("sl_at");
    }
}
