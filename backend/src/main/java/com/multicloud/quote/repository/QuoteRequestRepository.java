package com.multicloud.quote.repository;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.QuoteRequest;
import com.multicloud.quote.entity.VendorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {

    @EntityGraph(attributePaths = {"items", "items.vendor"})
    @Query("select q from QuoteRequest q where q.id = :id")
    Optional<QuoteRequest> findWithItemsById(Long id);

    /**
     * 조건별 견적 이력 조회. null 파라미터는 해당 조건을 적용하지 않는다.
     * 벤더 조건은 견적 항목에 그 벤더가 포함된 견적만 남긴다(중복 행이 생기지 않도록 exists 사용).
     */
    @Query(value = """
            select q from QuoteRequest q
            where (:region is null or q.region = :region)
              and (:os is null or q.os = :os)
              and (:from is null or q.createdAt >= :from)
              and (:to is null or q.createdAt < :to)
              and (:vendor is null or exists (
                    select 1 from QuoteItem i
                    where i.quoteRequest = q and i.vendor.name = :vendor))
            order by q.createdAt desc
            """,
            countQuery = """
                    select count(q) from QuoteRequest q
                    where (:region is null or q.region = :region)
                      and (:os is null or q.os = :os)
                      and (:from is null or q.createdAt >= :from)
                      and (:to is null or q.createdAt < :to)
                      and (:vendor is null or exists (
                            select 1 from QuoteItem i
                            where i.quoteRequest = q and i.vendor.name = :vendor))
                    """)
    Page<QuoteRequest> search(@Param("region") String region,
                              @Param("vendor") VendorType vendor,
                              @Param("os") OsType os,
                              @Param("from") OffsetDateTime from,
                              @Param("to") OffsetDateTime to,
                              Pageable pageable);
}
