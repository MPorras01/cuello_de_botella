-- Segmentos de tráfico en tiempo real
CREATE TABLE IF NOT EXISTS traffic_segments (
    id              BIGSERIAL PRIMARY KEY,
    segment_id      VARCHAR(100) NOT NULL,
    street_name     VARCHAR(255),
    current_speed   DECIMAL(6,2),
    free_flow_speed DECIMAL(6,2),
    speed_ratio     DECIMAL(4,3),
    source          VARCHAR(20),
    lat             DECIMAL(10,7),
    lng             DECIMAL(10,7),
    recorded_at     TIMESTAMPTZ DEFAULT NOW()
);

-- Snapshots históricos agregados (persistidos cada 5 minutos)
CREATE TABLE IF NOT EXISTS traffic_history (
    id               BIGSERIAL PRIMARY KEY,
    segment_id       VARCHAR(100) NOT NULL,
    segment_name     VARCHAR(255),
    speed_ratio      DECIMAL(4,3),
    congestion_level VARCHAR(20),
    ts               TIMESTAMPTZ NOT NULL
);

-- Suscripciones Web Push
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    endpoint    TEXT NOT NULL UNIQUE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Usuarios de la aplicación (login JWT)
CREATE TABLE IF NOT EXISTS app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
    created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- Informes de usuarios colocados en el mapa (capa de usuario)
CREATE TABLE IF NOT EXISTS user_reports (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    description TEXT,
    lat         DECIMAL(10,7) NOT NULL,
    lng         DECIMAL(10,7) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_reports_created ON user_reports(created_at DESC);

-- Grupos de chat
CREATE TABLE IF NOT EXISTS chat_groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(60) NOT NULL UNIQUE,
    description TEXT,
    created_by  VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_groups_created ON chat_groups(created_at ASC);

-- Mensajes de chat
CREATE TABLE IF NOT EXISTS chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES chat_groups(id) ON DELETE CASCADE,
    sender_username VARCHAR(50) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_messages_group ON chat_messages(group_id, created_at ASC);

-- Índices para consultas históricas
CREATE INDEX IF NOT EXISTS idx_history_ts      ON traffic_history(ts DESC);
CREATE INDEX IF NOT EXISTS idx_history_segment ON traffic_history(segment_id, ts DESC);

-- NOTA: Los comandos create_hypertable de TimescaleDB se ejecutan manualmente
-- en producción, ya que la extensión puede no estar disponible en entornos de test.
