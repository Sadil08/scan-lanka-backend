package com.scanlanka.shared.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sets the per-transaction Postgres GUC `app.current_user_id` so RLS policies scope owner tables
 * (cart/order/wishlist/address) to the caller (global/02 §6, T-2). Must be called inside the same
 * @Transactional as the queries; uses set_config(..., is_local=true) so it auto-resets at tx end.
 * JdbcTemplate shares the transaction-bound connection with JPA.
 */
@Component
public class RlsContext {

    private final JdbcTemplate jdbc;

    public RlsContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void setCurrentUser(long userId) {
        jdbc.queryForObject("select set_config('app.current_user_id', ?, true)",
            String.class, Long.toString(userId));
    }
}
