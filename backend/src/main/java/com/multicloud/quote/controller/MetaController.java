package com.multicloud.quote.controller;

import com.multicloud.quote.config.RegionCatalog;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** 프론트 폼의 셀렉트 박스를 서버 정의로 채우기 위한 메타 API. */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta", description = "견적 폼 구성용 메타데이터")
public class MetaController {

    private final RegionCatalog regionCatalog;

    public MetaController(RegionCatalog regionCatalog) {
        this.regionCatalog = regionCatalog;
    }

    @Operation(summary = "지원 리전/벤더/OS 목록")
    @GetMapping
    public MetaResponse meta() {
        return new MetaResponse(
                regionCatalog.logicalRegions(),
                List.of(VendorType.values()),
                List.of(OsType.values()));
    }

    public record MetaResponse(Set<String> regions, List<VendorType> vendors, List<OsType> osTypes) {
    }
}
