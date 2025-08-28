package com.worksphere.billing.infrastructure.repository;

import com.worksphere.billing.domain.model.BillingOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BillingOperationLogRepository
        extends JpaRepository<BillingOperationLog, String>, JpaSpecificationExecutor<BillingOperationLog> {
}
