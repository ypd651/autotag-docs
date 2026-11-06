-- ==============================================================================
-- AutoTag Docs - Schema de Base de Datos Relacional (PostgreSQL)
-- Compatible con DBeaver y administradores SQL
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Tabla de Usuarios (Gestión de usuarios y propiedad de documentos)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(80) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(150),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabla Principal de Documentos
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) DEFAULT 'application/pdf',
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESANDO',
    summary TEXT,
    chunks_count INT DEFAULT 0,
    vector_collection VARCHAR(100) DEFAULT 'autotag_documents',
    error_message TEXT,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_document_status CHECK (status IN ('PENDIENTE', 'PROCESANDO', 'LISTO', 'FALLIDO'))
);

-- Tabla Normalizada de Etiquetas Contextuales (Tags generadas por n8n RAG)
CREATE TABLE IF NOT EXISTS document_tags (
    id BIGSERIAL PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tag_name VARCHAR(100) NOT NULL,
    confidence_score NUMERIC(5,4) DEFAULT 1.0000,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_tag UNIQUE (document_id, tag_name)
);

-- Historial / Auditoría de Procesamiento de Webhooks y Estados
CREATE TABLE IF NOT EXISTS processing_logs (
    id BIGSERIAL PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    stage VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payload JSONB,
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices de Rendimiento para Búsquedas Rápidas
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_created_at ON documents(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_tags_tag ON document_tags(tag_name);
CREATE INDEX IF NOT EXISTS idx_document_tags_doc ON document_tags(document_id);
CREATE INDEX IF NOT EXISTS idx_processing_logs_doc ON processing_logs(document_id);

-- Función y Trigger para actualización automática de updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS trg_documents_updated_at ON documents;
CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Vista enriquecida para DBeaver y Frontend
CREATE OR REPLACE VIEW vw_document_catalog AS
SELECT 
    d.id,
    d.file_name,
    d.file_path,
    d.file_size,
    d.mime_type,
    d.status,
    d.summary,
    d.chunks_count,
    d.error_message,
    d.created_at,
    d.updated_at,
    COALESCE(
        json_agg(
            json_build_object('name', dt.tag_name, 'score', dt.confidence_score)
        ) FILTER (WHERE dt.tag_name IS NOT NULL), '[]'::json
    ) AS tags
FROM documents d
LEFT JOIN document_tags dt ON d.id = dt.document_id
GROUP BY d.id;

-- Datos Semilla (Para testing inicial en DBeaver)
INSERT INTO users (id, username, email, full_name, role)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'admin', 'admin@autotag.io', 'Administrador AutoTag', 'ADMIN')
ON CONFLICT (username) DO NOTHING;
