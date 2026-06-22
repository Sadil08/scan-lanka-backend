package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.Address;
import com.scanlanka.checkout.infra.AddressRepository;
import com.scanlanka.checkout.web.dto.AddressRequests.AddressInput;
import com.scanlanka.checkout.web.dto.AddressRequests.AddressView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Customer saved addresses (05 FR-CHECKOUT-8). */
@Service
public class AddressService {

    private final AddressRepository addresses;

    public AddressService(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<AddressView> list(long customerId) {
        return addresses.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
            .map(this::toView).toList();
    }

    @Transactional
    public AddressView create(long customerId, AddressInput input) {
        if (input.isDefault()) clearDefault(customerId);
        Address saved = addresses.save(new Address(customerId, input.label(), input.street(), input.city(),
            input.province(), input.postalCode(), input.phone(), input.email(), input.isDefault()));
        return toView(saved);
    }

    @Transactional
    public AddressView update(long customerId, Long id, AddressInput input) {
        Address a = own(customerId, id);
        if (input.isDefault()) clearDefault(customerId);
        a.setLabel(input.label());
        a.setStreet(input.street());
        a.setCity(input.city());
        a.setProvince(input.province());
        a.setPostalCode(input.postalCode());
        a.setPhone(input.phone());
        a.setEmail(input.email());
        a.setDefault(input.isDefault());
        return toView(addresses.save(a));
    }

    @Transactional
    public void delete(long customerId, Long id) {
        Address a = own(customerId, id);
        addresses.delete(a);
    }

    private Address own(long customerId, Long id) {
        return addresses.findByIdAndCustomerId(id, customerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
    }

    private void clearDefault(long customerId) {
        for (Address a : addresses.findByCustomerIdOrderByCreatedAtDesc(customerId)) {
            if (a.isDefault()) {
                a.setDefault(false);
                addresses.save(a);
            }
        }
    }

    private AddressView toView(Address a) {
        return new AddressView(a.getId(), a.getLabel(), a.getStreet(), a.getCity(), a.getProvince(),
            a.getPostalCode(), a.getPhone(), a.getEmail(), a.isDefault());
    }
}
