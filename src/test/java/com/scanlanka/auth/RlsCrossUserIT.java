package com.scanlanka.auth;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.cart.domain.Cart;
import com.scanlanka.cart.infra.CartRepository;
import com.scanlanka.shared.security.RlsContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Postgres RLS denies cross-user reads at the DB (07 RlsCrossUserIT, SEC-AUTH-1). */
class RlsCrossUserIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired RlsContext rls;
    @Autowired AppUserRepository users;
    @Autowired CartRepository carts;
    @Autowired PasswordEncoder encoder;

    @Test
    void cartRlsScopesRowsToCurrentUserGuc() {
        var userA = users.save(new com.scanlanka.auth.domain.AppUser(
            "rls-a@scanlanka.lk", encoder.encode("password123"), "A",
            com.scanlanka.auth.domain.Role.CUSTOMER));
        var userB = users.save(new com.scanlanka.auth.domain.AppUser(
            "rls-b@scanlanka.lk", encoder.encode("password123"), "B",
            com.scanlanka.auth.domain.Role.CUSTOMER));
        carts.save(new Cart(userA.getId()));
        carts.save(new Cart(userB.getId()));

        jdbc.execute("SET ROLE scanlanka_rls");
        try {
            Long visibleAsA = tx.execute(status -> {
                rls.setCurrentUser(userA.getId());
                return jdbc.queryForObject("select count(*) from cart", Long.class);
            });
            assertThat(visibleAsA).isEqualTo(1);

            Long visibleAsB = tx.execute(status -> {
                rls.setCurrentUser(userB.getId());
                return jdbc.queryForObject("select count(*) from cart", Long.class);
            });
            assertThat(visibleAsB).isEqualTo(1);
        } finally {
            jdbc.execute("RESET ROLE");
        }
    }
}
