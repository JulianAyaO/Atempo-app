package com.restaurant.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository outboxRepository;
    private final AuditService auditService;

    public OutboxProcessor(OutboxEventRepository outboxRepository, AuditService auditService) {
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelay = 5000)
    public void processPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findByProcessedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                log.debug("Procesando outbox event {}: {}", event.getId(), event.getEventType());
                auditService.logEvent(
                    "OUTBOX_" + event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getPayload(),
                    "outbox-processor"
                );
                event.setProcessedAt(LocalDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Error procesando outbox event {}", event.getId(), e);
            }
        }
    }
}
