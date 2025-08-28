package com.worksphere.billing.infrastructure.repository;

import com.worksphere.billing.domain.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    List<Invoice> findByTenantId(String tenantId);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.issueDate BETWEEN :startDate AND :endDate")
    List<Invoice> findByTenantIdAndDateRange(String tenantId, Instant startDate, Instant endDate);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status = :status")
    List<Invoice> findByTenantIdAndStatus(String tenantId, String status);

    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.issueDate BETWEEN :startDate AND :endDate AND i.status = :status")
    List<Invoice> findByTenantIdAndDateRangeAndStatus(String tenantId, Instant startDate, Instant endDate, String status);
}
