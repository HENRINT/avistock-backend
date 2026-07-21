-- Tabla de notificaciones persistentes dueño <-> cajero.
-- Seguro de correr las veces que quieras: usa IF NOT EXISTS, no borra nada.
CREATE TABLE IF NOT EXISTS notificaciones_caja (
    id_notificacion INT PRIMARY KEY AUTO_INCREMENT,
    tipo VARCHAR(30) NOT NULL COMMENT 'CORTE_SOLICITADO | CIERRE_DISPONIBLE | REPORTE_ENVIVO',
    mensaje VARCHAR(255) NOT NULL,
    id_cierre INT NULL,
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Verificación
DESCRIBE notificaciones_caja;
