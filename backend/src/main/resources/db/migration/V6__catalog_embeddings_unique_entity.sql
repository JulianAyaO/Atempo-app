-- Evita duplicados al reindexar el catálogo RAG.
DELETE FROM catalog_embeddings a
USING catalog_embeddings b
WHERE a.id > b.id
  AND a.entity_type = b.entity_type
  AND a.entity_id = b.entity_id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_catalog_embeddings_entity
    ON catalog_embeddings(entity_type, entity_id);
