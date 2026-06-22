package com.scanlanka.wishlist.app;

import com.scanlanka.catalog.app.ProductQueryService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductChipDTO;
import com.scanlanka.wishlist.domain.WishlistItem;
import com.scanlanka.wishlist.infra.WishlistItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Customer wishlist (03-wishlist). Visible products only; archived/deleted drop silently. */
@Service
public class WishlistService {

    private final WishlistItemRepository items;
    private final ProductRepository products;
    private final ProductQueryService chips;

    public WishlistService(WishlistItemRepository items, ProductRepository products,
                           ProductQueryService chips) {
        this.items = items;
        this.products = products;
        this.chips = chips;
    }

    @Transactional(readOnly = true)
    public List<ProductChipDTO> list(long customerId) {
        List<Long> productIds = items.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
            .map(WishlistItem::getProductId)
            .toList();
        return chips.chipsForVisibleProductIds(productIds);
    }

    @Transactional
    public boolean add(long customerId, long productId) {
        requireVisibleProduct(productId);
        if (items.existsByCustomerIdAndProductId(customerId, productId)) {
            return false;
        }
        items.save(new WishlistItem(customerId, productId));
        return true;
    }

    @Transactional
    public boolean remove(long customerId, long productId) {
        if (!items.existsByCustomerIdAndProductId(customerId, productId)) {
            return false;
        }
        items.deleteByCustomerIdAndProductId(customerId, productId);
        return true;
    }

    @Transactional
    public List<ProductChipDTO> merge(long customerId, List<Long> guestProductIds) {
        if (guestProductIds != null) {
            for (Long productId : guestProductIds) {
                if (productId == null) continue;
                products.findById(productId)
                    .filter(p -> p.isActive() && !p.isArchived())
                    .ifPresent(p -> {
                        if (!items.existsByCustomerIdAndProductId(customerId, productId)) {
                            items.save(new WishlistItem(customerId, productId));
                        }
                    });
            }
        }
        return list(customerId);
    }

    private void requireVisibleProduct(long productId) {
        boolean visible = products.findById(productId)
            .filter(p -> p.isActive() && !p.isArchived())
            .isPresent();
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
    }
}
