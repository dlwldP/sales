package com.multicloud.quote.repository;

import com.multicloud.quote.entity.Vendor;
import com.multicloud.quote.entity.VendorType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByName(VendorType name);
}
