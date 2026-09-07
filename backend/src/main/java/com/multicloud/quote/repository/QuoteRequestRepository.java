package com.multicloud.quote.repository;

import com.multicloud.quote.entity.QuoteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {

    @EntityGraph(attributePaths = {"items", "items.vendor"})
    @Query("select q from QuoteRequest q where q.id = :id")
    Optional<QuoteRequest> findWithItemsById(Long id);

    Page<QuoteRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
