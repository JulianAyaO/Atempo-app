package com.restaurant.conversation;

import com.restaurant.catalog.CatalogService;
import com.restaurant.catalog.Product;
import com.restaurant.catalog.ProductRepository;
import com.restaurant.common.dto.ChatTurnRequest;
import com.restaurant.common.dto.ChatTurnResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationTurnRepository turnRepo;
    private final CatalogRAGService ragService;
    private final CatalogService catalogService;
    private final ProductRepository productRepository;
    private final ConversationOrchestrator orchestrator;

    private ConversationService self;

    public ConversationService(ConversationTurnRepository turnRepo,
                               CatalogRAGService ragService,
                               CatalogService catalogService,
                               ProductRepository productRepository,
                               ConversationOrchestrator orchestrator) {
        this.turnRepo = turnRepo;
        this.ragService = ragService;
        this.catalogService = catalogService;
        this.productRepository = productRepository;
        this.orchestrator = orchestrator;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy ConversationService self) {
        this.self = self;
    }

    @PostConstruct
    public void init() {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(3000);
                self.indexCatalog();
            } catch (Exception e) {
                log.warn("⚠ No se pudo indexar catálogo (Ollama no disponible?): {}", e.getMessage());
            }
        });
    }

    public ChatTurnResponse processMessage(ChatTurnRequest request) {
        return orchestrator.processMessage(request);
    }

    @Transactional
    public void indexCatalog() {
        List<Product> products = productRepository.findAllActiveWithCategory();
        int indexed = 0;
        for (Product p : products) {
            Product full = productRepository.findByIdWithIngredients(p.getId());
            if (full == null) continue;
            String text = catalogService.buildProductTextForEmbedding(full);
            float[] emb = ragService.generateEmbedding(text);
            if (emb != null) {
                ragService.storeEmbedding("PRODUCT", p.getId(), text, emb);
                indexed++;
            }
        }
        log.info("✅ Catálogo indexado: {}/{} productos", indexed, products.size());
    }

    public List<ConversationTurn> getHistory(String sessionId) {
        return turnRepo.findBySessionIdOrderByCreatedAt(sessionId);
    }

    @Transactional
    public void clearHistory(String sessionId) {
        List<ConversationTurn> turns = turnRepo.findBySessionIdOrderByCreatedAt(sessionId);
        for (ConversationTurn t : turns) {
            turnRepo.delete(t);
        }
        log.info("Historial de chat borrado para sessionId={}", sessionId);
    }
}
