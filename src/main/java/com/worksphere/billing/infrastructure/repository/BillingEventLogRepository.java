package com.worksphere.billing.infrastructure.repository;

import com.worksphere.billing.domain.event.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingEventLogRepository extends JpaRepository<BillingEvent, String> {
    Optional<BillingEvent> findByEventId(String eventId);
}