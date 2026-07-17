package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.app.BrowseFilters;
import com.scanlanka.catalog.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native browse query with COALESCE price sort (02-storefront-browse §4.1). */
@Repository
public class ProductBrowseQueries {

    @PersistenceContext
    private EntityManager em;

    public Page<Product> browse(BrowseFilters filters, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE active = true AND archived = false");
        Map<String, Object> params = new HashMap<>();

        if (filters.q() != null && !filters.q().isBlank()) {
            where.append(" AND name ILIKE :q");
            params.put("q", "%" + filters.q().trim() + "%");
        }
        if (filters.category() != null && !filters.category().isBlank()) {
            where.append(" AND category = :category");
            params.put("category", filters.category().trim());
        }
        if (filters.parentId() != null) {
            where.append(" AND parent_product_id = :parentId");
            params.put("parentId", filters.parentId());
        }

        String orderBy = switch (filters.normalizedSort()) {
            case "price_asc" -> " ORDER BY COALESCE(single_price_cents, price_range_min_cents, 0) ASC, name ASC";
            case "price_desc" -> " ORDER BY COALESCE(single_price_cents, price_range_max_cents, 0) DESC, name ASC";
            case "name" -> " ORDER BY name ASC";
            // Default listing order = the owner's sheet order (display_order, V46/V47);
            // unordered (admin-created) products fall after the seeded ones, newest first.
            default -> " ORDER BY display_order ASC, created_at DESC, id DESC";
        };

        Query countQ = em.createNativeQuery("SELECT COUNT(*) FROM product" + where);
        params.forEach(countQ::setParameter);
        long total = ((Number) countQ.getSingleResult()).longValue();

        Query dataQ = em.createNativeQuery(
            "SELECT * FROM product" + where + orderBy + " LIMIT :limit OFFSET :offset", Product.class);
        params.forEach(dataQ::setParameter);
        dataQ.setParameter("limit", pageable.getPageSize());
        dataQ.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Product> content = dataQ.getResultList();
        return new PageImpl<>(content, pageable, total);
    }
}
