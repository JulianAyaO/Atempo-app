package com.restaurant.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Async
    public void logEvent(String eventType, String entityType, String entityId, Object payload, String actor) {
        try {
            AuditEvent event = new AuditEvent();
            event.setEventType(eventType);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setActor(actor);
            repository.save(event);
            log.debug("Audit: {} {} {} by {}", eventType, entityType, entityId, actor);
        } catch (Exception e) {
            log.error("Error guardando evento de auditoría", e);
        }
    }

    public java.util.List<AuditEvent> getRecentEvents() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }
}
