package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Address> findByIdAndCustomerId(Long id, Long customerId);
}
