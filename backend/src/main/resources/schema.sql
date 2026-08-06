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

-- Índices para consultas históricas
CREATE INDEX IF NOT EXISTS idx_history_ts      ON traffic_history(ts DESC);
CREATE INDEX IF NOT EXISTS idx_history_segment ON traffic_history(segment_id, ts DESC);

-- NOTA: Los comandos create_hypertable de TimescaleDB se ejecutan manualmente
-- en producción, ya que la extensión puede no estar disponible en entornos de test.
