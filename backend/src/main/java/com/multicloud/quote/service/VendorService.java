package com.multicloud.quote.service;

import com.multicloud.quote.entity.Vendor;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorService {

    private final VendorRepository vendorRepository;

    public VendorService(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    /** 벤더 마스터는 고정 목록이므로 최초 참조 시 자동 생성한다. */
    @Transactional
    public Vendor getOrCreate(VendorType type) {
        return vendorRepository.findByName(type)
                .orElseGet(() -> vendorRepository.save(new Vendor(type)));
    }
}
