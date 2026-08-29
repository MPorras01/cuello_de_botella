#!/bin/bash
# =============================================================================
# Backup de PostgreSQL — Trancones Medellín
# Uso: ./scripts/backup-db.sh [directorio_destino]
# =============================================================================
set -euo pipefail

BACKUP_DIR="${1:-./backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILENAME="trancones_${TIMESTAMP}.sql.gz"
CONTAINER="trancones-postgres"
DB_NAME="trancones"
DB_USER="${DB_USER:-postgres}"

# Crear directorio si no existe
mkdir -p "$BACKUP_DIR"

echo "📦 Iniciando backup de PostgreSQL..."
echo "   Container: $CONTAINER"
echo "   Database:  $DB_NAME"
echo "   Destino:   $BACKUP_DIR/$FILENAME"

# Ejecutar pg_dump dentro del contenedor y comprimir
docker exec "$CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" \
    | gzip > "$BACKUP_DIR/$FILENAME"

# Verificar tamaño
SIZE=$(du -h "$BACKUP_DIR/$FILENAME" | cut -f1)
echo "✅ Backup completado: $FILENAME ($SIZE)"

# Limpiar backups con más de 30 días
echo "🧹 Limpiando backups antiguos (>30 días)..."
find "$BACKUP_DIR" -name "trancones_*.sql.gz" -mtime +30 -delete 2>/dev/null || true

# Listar backups disponibles
echo ""
echo "📋 Backups disponibles:"
ls -lh "$BACKUP_DIR"/trancones_*.sql.gz 2>/dev/null | tail -5
echo ""
echo "🔄 Para restaurar:"
echo "   gunzip -c $BACKUP_DIR/$FILENAME | docker exec -i $CONTAINER psql -U $DB_USER $DB_NAME"
