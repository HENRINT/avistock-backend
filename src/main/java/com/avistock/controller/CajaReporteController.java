package com.avistock.controller;

import com.avistock.util.AuthGuard;
import io.javalin.http.Context;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CajaReporteController {

    private final EntityManagerFactory emf;

    // NUEVO: las notificaciones ahora se guardan en la tabla `notificaciones_caja`
    // (persistente, sobrevive a un reinicio del backend) en vez de variables en memoria.

    public CajaReporteController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // ==========================================
    // 1. POST - Apertura de Caja Real
    // ==========================================
    public void abrirCajaTurno(Context ctx) {
        Map<?, ?> body = ctx.bodyAsClass(Map.class);
        Integer idUsuario = (Integer) body.get("id_usuario");

        if (body.get("fondo_inicial") == null) {
            ctx.status(400).json(Map.of("error", "El campo 'fondo_inicial' es obligatorio."));
            return;
        }
        BigDecimal fondoInicial = new BigDecimal(body.get("fondo_inicial").toString());
        String notasApertura = (String) body.get("notas_apertura");

        EntityManager em = emf.createEntityManager();
        try {
            String sqlCheck = "SELECT COUNT(*) FROM cierres_caja WHERE estado_cierre = 'Abierta'";
            Number abiertas = (Number) em.createNativeQuery(sqlCheck).getSingleResult();

            if (abiertas.intValue() > 0) {
                ctx.status(400).json(Map.of("error", "Ya existe un turno de caja activo actualmente."));
                return;
            }

            em.getTransaction().begin();

            String sqlInsert = "INSERT INTO cierres_caja (id_usuario, fecha_apertura, fondo_inicial, notas_apertura, total_sold_efectivo, total_mermas_dia, efectivo_real_contado, estado_cierre) " +
                    "VALUES (?, NOW(), ?, ?, 0.00, 0.00, 0.00, 'Abierta')";

            em.createNativeQuery(sqlInsert)
                    .setParameter(1, idUsuario)
                    .setParameter(2, fondoInicial)
                    .setParameter(3, notasApertura)
                    .executeUpdate();

            em.getTransaction().commit();

            ctx.status(201).json(Map.of("success", true, "mensaje", "Turno abierto con éxito en la base de datos."));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(500).json(Map.of("error", "Error crítico en base de datos: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // ==========================================
    // 2. POST - Cierre de Caja Real con Cálculos de BD
    // ==========================================
    public void cerrarCajaDefinitivo(Context ctx) {
        Map<?, ?> body = ctx.bodyAsClass(Map.class);

        if (body.get("efectivo_real_contado") == null) {
            ctx.status(400).json(Map.of("error", "El campo 'efectivo_real_contado' es obligatorio."));
            return;
        }
        BigDecimal efectivoRealContado = new BigDecimal(body.get("efectivo_real_contado").toString());
        String notasCierre = (String) body.get("notas_cierre");

        EntityManager em = emf.createEntityManager();
        try {
            String sqlGetActiva = "SELECT id_cierre, fondo_inicial FROM cierres_caja WHERE estado_cierre = 'Abierta' LIMIT 1";
            List<Object[]> activaRow = em.createNativeQuery(sqlGetActiva).getResultList();

            if (activaRow.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No hay ningún turno abierto para cerrar."));
                return;
            }

            Integer idCierre = (Integer) activaRow.get(0)[0];
            BigDecimal fondoInicial = (BigDecimal) activaRow.get(0)[1];

            // CORREGIDO: la tabla se llama 'ventas_mostrador' (no 'ventas'), con columnas
            // 'total_venta' y 'fecha_hora' (no 'total'/'fecha'). La tabla 'ventas' no existe
            // en tu base de datos real; esto habría tronado con error de tabla inexistente.
            String sqlVentasHoy = "SELECT COALESCE(SUM(total_venta), 0) FROM ventas_mostrador WHERE DATE(fecha_hora) = CURRENT_DATE()";
            Number ventasHoy = (Number) em.createNativeQuery(sqlVentasHoy).getSingleResult();
            BigDecimal totalSoldEfectivo = new BigDecimal(ventasHoy.toString());

            // CONEXIÓN REAL: Suma el costo real de mermas del día (calculado: kg perdidos x precio/kg del producto)
            // La tabla 'mermas' no tiene columna 'costo_total' propia, así que se calcula vía JOIN con 'productos'.
            String sqlMermasHoy = "SELECT COALESCE(SUM(m.peso_perdido_kg * p.precio_kg), 0) " +
                    "FROM mermas m INNER JOIN productos p ON m.id_producto = p.id_producto " +
                    "WHERE DATE(m.fecha) = CURRENT_DATE()";
            Number mermasHoy = (Number) em.createNativeQuery(sqlMermasHoy).getSingleResult();
            BigDecimal totalMermasDia = new BigDecimal(mermasHoy.toString());

            // Fórmulas matemáticas reales
            BigDecimal dineroEsperado = fondoInicial.add(totalSoldEfectivo);
            BigDecimal discrepancia = efectivoRealContado.subtract(dineroEsperado);

            em.getTransaction().begin();

            String sqlUpdate = "UPDATE cierres_caja SET " +
                    "fecha_cierre = NOW(), " +
                    "total_sold_efectivo = ?, " +
                    "total_mermas_dia = ?, " +
                    "efectivo_real_contado = ?, " +
                    "discrepancia = ?, " +
                    "estado_cierre = 'Cerrada', " +
                    "notas_cierre = ? " +
                    "WHERE id_cierre = ?";

            em.createNativeQuery(sqlUpdate)
                    .setParameter(1, totalSoldEfectivo)
                    .setParameter(2, totalMermasDia)
                    .setParameter(3, efectivoRealContado)
                    .setParameter(4, discrepancia)
                    .setParameter(5, notasCierre)
                    .setParameter(6, idCierre)
                    .executeUpdate();

            em.getTransaction().commit();

            // Marca como leídas las solicitudes de corte que el dueño le hizo al cajero
            em.getTransaction().begin();
            em.createNativeQuery("UPDATE notificaciones_caja SET leida = true WHERE tipo = 'CORTE_SOLICITADO' AND leida = false")
                    .executeUpdate();

            // NUEVO: avisa al dueño que hay un reporte de cierre nuevo disponible
            em.createNativeQuery("INSERT INTO notificaciones_caja (tipo, mensaje, id_cierre, leida, fecha_creacion) VALUES ('CIERRE_DISPONIBLE', ?, ?, false, NOW())")
                    .setParameter(1, "Se cerró la caja (Folio #CR-" + idCierre + "). Reporte disponible para revisar.")
                    .setParameter(2, idCierre)
                    .executeUpdate();
            em.getTransaction().commit();

            ctx.status(200).json(Map.of(
                    "success", true,
                    "id_cierre", idCierre,
                    "total_ventas", totalSoldEfectivo,
                    "discrepancia", discrepancia
            ));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(500).json(Map.of("error", "Error al guardar el cierre: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // ==========================================
    // 3. GET - Carga del Dashboard del Dueño
    // ==========================================
    @SuppressWarnings("unchecked")
    public void getCajaDashboard(Context ctx) {
        // CORREGIDO: este endpoint también lo usa la pantalla del cajero (cierre_caja.html,
        // tabla "Reportes del día de hoy"), no solo el dueño — restringirlo a dueño-only
        // rompía esa pantalla. Los datos que da (totales, cierres recientes) no son tan
        // sensibles como el PDF descargable en sí (ese sí sigue protegido, solo dueño).
        Map<String, Object> responseData = new HashMap<>();

        try (EntityManager em = emf.createEntityManager()) {
            String sqlTotalVendido = "SELECT COALESCE(SUM(total_sold_efectivo), 0) FROM cierres_caja WHERE YEARWEEK(fecha_apertura, 1) = YEARWEEK(CURRENT_DATE(), 1)";
            Number totalVendido = (Number) em.createNativeQuery(sqlTotalVendido).getSingleResult();

            String sqlTotalMermas = "SELECT COALESCE(SUM(total_mermas_dia), 0) FROM cierres_caja WHERE YEARWEEK(fecha_apertura, 1) = YEARWEEK(CURRENT_DATE(), 1)";
            Number totalMermas = (Number) em.createNativeQuery(sqlTotalMermas).getSingleResult();

            String sqlDiasActivos = "SELECT COUNT(DISTINCT DATE(fecha_apertura)) FROM cierres_caja WHERE YEARWEEK(fecha_apertura, 1) = YEARWEEK(CURRENT_DATE(), 1)";
            Number diasActivos = (Number) em.createNativeQuery(sqlDiasActivos).getSingleResult();

            responseData.put("total_vendido", totalVendido.doubleValue());
            responseData.put("dias_activos", diasActivos.intValue());
            responseData.put("total_mermas", totalMermas.doubleValue());

            String sqlCierres = "SELECT c.id_cierre, DATE_FORMAT(c.fecha_cierre, '%d/%m/%Y - %H:%i hrs'), u.nombre, c.efectivo_real_contado, " +
                    "c.fondo_inicial, c.total_mermas_dia " +
                    "FROM cierres_caja c " +
                    "INNER JOIN usuarios u ON c.id_usuario = u.id_usuario " +
                    "WHERE c.estado_cierre = 'Cerrada' " +
                    "ORDER BY c.fecha_cierre DESC LIMIT 5";

            List<Object[]> rawCierres = em.createNativeQuery(sqlCierres).getResultList();
            List<Map<String, Object>> listaCierres = new ArrayList<>();
            List<Integer> idsCierres = new ArrayList<>();

            for (Object[] row : rawCierres) {
                Map<String, Object> item = new HashMap<>();
                Integer idCierre = (Integer) row[0];
                item.put("id_cierre", idCierre);
                item.put("fecha_hora", row[1] != null ? row[1] : "En proceso");
                item.put("cerrado_por", row[2]);
                item.put("total", row[3]);
                // NUEVO: antes la tabla "Reportes del día de hoy" del cajero mostraba estas
                // columnas siempre en blanco ("—"). fondo_inicial y total_mermas_dia ya estaban
                // guardados en cierres_caja, solo faltaba incluirlos en la respuesta.
                item.put("inicio_caja", row[4]);
                item.put("mermas", row[5]);
                item.put("venta_pie", 0.0);
                item.put("venta_camara", 0.0);
                listaCierres.add(item);
                idsCierres.add(idCierre);
            }

            // NUEVO: desglose de venta por producto (Pie/Cámara) para cada uno de esos cierres —
            // se calcula sumando detalle_venta agrupado por producto, para las ventas que
            // pertenecen a cada turno (v.id_cierre).
            if (!idsCierres.isEmpty()) {
                String sqlVentaPorProducto = "SELECT v.id_cierre, p.nombre, SUM(dv.subtotal) " +
                        "FROM detalle_venta dv " +
                        "INNER JOIN productos p ON dv.id_producto = p.id_producto " +
                        "INNER JOIN ventas_mostrador v ON dv.id_venta = v.id_venta " +
                        "WHERE v.id_cierre IN (:ids) " +
                        "GROUP BY v.id_cierre, p.id_producto, p.nombre";
                List<Object[]> filasVentaProducto = em.createNativeQuery(sqlVentaPorProducto)
                        .setParameter("ids", idsCierres)
                        .getResultList();

                for (Object[] fila : filasVentaProducto) {
                    Integer idCierreFila = (Integer) fila[0];
                    String nombreLower = fila[1].toString().toLowerCase();
                    double monto = ((Number) fila[2]).doubleValue();

                    for (Map<String, Object> item : listaCierres) {
                        if (item.get("id_cierre").equals(idCierreFila)) {
                            if (nombreLower.contains("cámara") || nombreLower.contains("camara")) {
                                item.put("venta_camara", monto);
                            } else if (nombreLower.contains("pie")) {
                                item.put("venta_pie", monto);
                            }
                        }
                    }
                }
            }

            responseData.put("cierres_recientes", listaCierres);

            ctx.status(200).json(responseData);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================
    // NUEVO - GET: Historial REAL de notificaciones (reemplaza a los paneles
    // de "Alertas" con contenido fijo/decorativo que no tenían nada que ver
    // con lo que en verdad pasa en tu caja). Se usa igual en las 3 pantallas
    // del dueño para que muestren siempre lo mismo.
    // ==========================================
    @SuppressWarnings("unchecked")
    public void obtenerHistorialNotificaciones(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        try (EntityManager em = emf.createEntityManager()) {
            String sql = "SELECT tipo, mensaje, leida, DATE_FORMAT(fecha_creacion, '%d/%m/%Y - %H:%i hrs'), id_cierre, id_notificacion " +
                    "FROM notificaciones_caja " +
                    "WHERE tipo IN ('CORTE_SOLICITADO', 'CIERRE_DISPONIBLE', 'REPORTE_ENVIVO') " +
                    "ORDER BY fecha_creacion DESC LIMIT 15";
            List<Object[]> filas = em.createNativeQuery(sql).getResultList();

            List<Map<String, Object>> historial = new ArrayList<>();
            for (Object[] row : filas) {
                Map<String, Object> item = new HashMap<>();
                item.put("tipo", row[0]);
                item.put("mensaje", row[1]);
                item.put("leida", row[2]);
                item.put("fecha", row[3]);
                item.put("id_cierre", row[4]);
                item.put("id_notificacion", row[5]);
                historial.add(item);
            }
            ctx.status(200).json(Map.of("historial", historial));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al consultar historial: " + e.getMessage()));
        }
    }

    // ==========================================
    // NUEVO - GET: TODOS los cierres terminados (sin límite de 5), para el
    // botón "Ver todos los reportes" — cada uno con su folio, fecha, cajero,
    // total y su propio id para poder descargar el PDF de ese cierre exacto.
    // ==========================================
    @SuppressWarnings("unchecked")
    public void obtenerTodosLosCierres(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        try (EntityManager em = emf.createEntityManager()) {
            String sql = "SELECT c.id_cierre, DATE_FORMAT(c.fecha_cierre, '%d/%m/%Y - %H:%i hrs'), u.nombre, c.total_sold_efectivo, c.efectivo_real_contado, c.discrepancia " +
                    "FROM cierres_caja c " +
                    "INNER JOIN usuarios u ON c.id_usuario = u.id_usuario " +
                    "WHERE c.estado_cierre = 'Cerrada' " +
                    "ORDER BY c.fecha_cierre DESC";
            List<Object[]> filas = em.createNativeQuery(sql).getResultList();

            List<Map<String, Object>> lista = new ArrayList<>();
            for (Object[] row : filas) {
                Map<String, Object> item = new HashMap<>();
                item.put("id_cierre", row[0]);
                item.put("fecha_hora", row[1]);
                item.put("cerrado_por", row[2]);
                item.put("total_vendido", row[3]);
                item.put("efectivo_contado", row[4]);
                item.put("discrepancia", row[5]);
                lista.add(item);
            }
            ctx.status(200).json(Map.of("cierres", lista));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al consultar cierres: " + e.getMessage()));
        }
    }

    // ==========================================
    // 4. GET - Detalle de Cierre Individual
    // ==========================================
    public void getDetalleCierre(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        String idParam = ctx.pathParam("id");

        try (EntityManager em = emf.createEntityManager()) {
            String sqlDetalle = "SELECT c.id_cierre, u.nombre, DATE_FORMAT(c.fecha_cierre, '%d/%m/%Y - %H:%i %p'), " +
                    "c.fondo_inicial, c.total_sold_efectivo, c.total_mermas_dia, c.efectivo_real_contado, c.discrepancia " +
                    "FROM cierres_caja c " +
                    "INNER JOIN usuarios u ON c.id_usuario = u.id_usuario " +
                    "WHERE c.id_cierre = ?";

            Object[] row = (Object[]) em.createNativeQuery(sqlDetalle)
                    .setParameter(1, Integer.parseInt(idParam))
                    .getSingleResult();

            if (row != null) {
                Map<String, Object> detalle = new HashMap<>();
                detalle.put("folio", "CR-" + row[0]);
                detalle.put("cerrado_por", row[1]);
                detalle.put("fecha_hora", row[2]);
                detalle.put("fondo_inicial", row[3]);
                detalle.put("ventas_efectivo", row[4]);
                detalle.put("gastos_mermas", row[5]);
                detalle.put("total_declarado", row[6]);
                detalle.put("discrepancia", row[7]);

                ctx.status(200).json(detalle);
            } else {
                ctx.status(404).json(Map.of("error", "No encontrado"));
            }
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    // ==========================================
    // 5. POST - Dueño solicita Corte Parcial
    // ==========================================
    public void solicitarCorteParcial(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        Map<?, ?> body = ctx.bodyAsClass(Map.class);
        String mensaje = (String) body.get("mensaje");
        String textoFinal = mensaje != null && !mensaje.isBlank() ? mensaje : "El dueño solicita reporte en tiempo real.";

        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO notificaciones_caja (tipo, mensaje, leida, fecha_creacion) VALUES ('CORTE_SOLICITADO', ?, false, NOW())")
                    .setParameter(1, textoFinal)
                    .executeUpdate();
            em.getTransaction().commit();
            ctx.status(200).json(Map.of("success", true, "message", "Solicitud registrada."));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al registrar la solicitud: " + e.getMessage()));
        }
    }

    // ==========================================
    // 6. GET - Notificaciones en tiempo real (ambas direcciones)
    // ==========================================
    public void obtenerNotificacionesPendientes(Context ctx) {
        try (EntityManager em = emf.createEntityManager()) {
            Map<String, Object> respuesta = new HashMap<>();

            // Dirección: dueño -> cajero (la ve el cajero en los 5 módulos de admin)
            List<Object[]> corte = em.createNativeQuery(
                    "SELECT mensaje FROM notificaciones_caja WHERE tipo = 'CORTE_SOLICITADO' AND leida = false ORDER BY fecha_creacion DESC LIMIT 1"
            ).getResultList();
            respuesta.put("cortes_solicitados_pendientes", !corte.isEmpty());
            respuesta.put("mensaje", corte.isEmpty() ? "" : corte.get(0)[0].toString());

            // Dirección: cajero -> dueño (las ve el dueño)
            List<Object[]> cierre = em.createNativeQuery(
                    "SELECT mensaje, id_cierre FROM notificaciones_caja WHERE tipo = 'CIERRE_DISPONIBLE' AND leida = false ORDER BY fecha_creacion DESC LIMIT 1"
            ).getResultList();
            respuesta.put("reporte_cierre_disponible", !cierre.isEmpty());
            respuesta.put("mensaje_cierre_disponible", cierre.isEmpty() ? "" : cierre.get(0)[0].toString());
            respuesta.put("id_cierre_disponible", cierre.isEmpty() ? null : cierre.get(0)[1]);

            List<Object[]> enVivo = em.createNativeQuery(
                    "SELECT id_notificacion, mensaje FROM notificaciones_caja WHERE tipo = 'REPORTE_ENVIVO' AND leida = false ORDER BY fecha_creacion DESC LIMIT 1"
            ).getResultList();
            respuesta.put("reporte_envivo_disponible", !enVivo.isEmpty());
            respuesta.put("id_notificacion_envivo", enVivo.isEmpty() ? null : enVivo.get(0)[0]);
            respuesta.put("mensaje_envivo_disponible", enVivo.isEmpty() ? "" : enVivo.get(0)[1].toString());

            ctx.status(200).json(respuesta);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al consultar notificaciones: " + e.getMessage()));
        }
    }

    // NUEVO: el dueño marca como leídas sus propias notificaciones (cierre disponible / en vivo disponible)
    public void marcarNotificacionesDuenoLeidas(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.createNativeQuery("UPDATE notificaciones_caja SET leida = true WHERE tipo IN ('CIERRE_DISPONIBLE', 'REPORTE_ENVIVO') AND leida = false")
                    .executeUpdate();
            em.getTransaction().commit();
            ctx.status(200).json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al marcar notificaciones: " + e.getMessage()));
        }
    }

    // ==========================================
    // 7. GET - Generación de Reporte Real Descargable
    // ==========================================
    public void descargarReportePdf(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        String idParam = ctx.pathParam("id");

        try (EntityManager em = emf.createEntityManager()) {
            String sqlPdf = "SELECT c.id_cierre, u.nombre, DATE_FORMAT(c.fecha_cierre, '%d/%m/%Y %H:%i'), " +
                    "c.fondo_inicial, c.total_sold_efectivo, c.total_mermas_dia, c.efectivo_real_contado, c.discrepancia " +
                    "FROM cierres_caja c " +
                    "INNER JOIN usuarios u ON c.id_usuario = u.id_usuario " +
                    "WHERE c.id_cierre = ?";

            Object[] row = (Object[]) em.createNativeQuery(sqlPdf)
                    .setParameter(1, Integer.parseInt(idParam))
                    .getSingleResult();

            if (row == null) {
                ctx.status(404).json(Map.of("error", "Folio inexistente."));
                return;
            }

            // Construcción del documento de auditoría real
            String reporteText = "==================================================\n" +
                    "            AVISTOCK - REPORTES DE CAJA          \n" +
                    "==================================================\n" +
                    "FOLIO REPORTADO   : CR-" + row[0] + "\n" +
                    "AUDITADO POR      : " + row[1] + "\n" +
                    "FECHA DE CIERRE   : " + row[2] + "\n" +
                    "--------------------------------------------------\n" +
                    "Fondo Apertura    : $" + row[3] + "\n" +
                    "Ventas del Turno  : $" + row[4] + "\n" +
                    "Mermas en Sistema : $" + row[5] + "\n" +
                    "--------------------------------------------------\n" +
                    "EFECTIVO CONTADO  : $" + row[6] + "\n" +
                    "DISCREPANCIA FINAL: $" + row[7] + "\n" +
                    "==================================================\n" +
                    "      Documento Oficial de Control de Caja      \n" +
                    "==================================================\n";

            // CONEXIÓN SEGURA DE DESCARGA: Se envía con el Content-Type correcto para archivos de texto estructurado oficiales
            ctx.contentType("text/plain");
            ctx.header("Content-Disposition", "attachment; filename=Reporte_Caja_Folio_" + idParam + ".txt");
            ctx.result(reporteText.getBytes());

        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al compilar el reporte: " + e.getMessage()));
        }
    }

    // ==========================================
    // 8. GET - Reporte Parcial DESCARGABLE del turno abierto (sin cerrar caja)
    // NUEVO: para cuando el dueño pide el corte con urgencia y no hay que
    // esperar hasta la hora de cierre. Es un "corte de caja" de solo lectura:
    // calcula los números del turno actual EN VIVO, pero no modifica nada en
    // la base de datos (no cierra la caja, no descuenta stock, no cambia estado).
    // ==========================================

    // 8a. GET - Datos automáticos para pre-llenar el formulario del cajero
    // (fondo inicial, ventas de hoy por producto, mermas de hoy). Sin efectos
    // secundarios — solo lectura, no genera ninguna notificación todavía.
    public void obtenerDatosAutomaticosReporte(Context ctx) {
        try (EntityManager em = emf.createEntityManager()) {
            String sqlAbierta = "SELECT c.fondo_inicial FROM cierres_caja c WHERE c.estado_cierre = 'Abierta' LIMIT 1";
            List<Object> filaAbierta = em.createNativeQuery(sqlAbierta).getResultList();
            if (filaAbierta.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No hay ninguna caja abierta ahora mismo."));
                return;
            }
            BigDecimal fondoInicial = (BigDecimal) filaAbierta.get(0);

            String sqlVentaPorProducto = "SELECT p.nombre, COALESCE(SUM(dv.subtotal),0) " +
                    "FROM detalle_venta dv " +
                    "INNER JOIN productos p ON dv.id_producto = p.id_producto " +
                    "INNER JOIN ventas_mostrador v ON dv.id_venta = v.id_venta " +
                    "WHERE DATE(v.fecha_hora) = CURRENT_DATE() " +
                    "GROUP BY p.id_producto, p.nombre";
            List<Object[]> filasProducto = em.createNativeQuery(sqlVentaPorProducto).getResultList();
            double ventasPie = 0, ventasCamara = 0;
            for (Object[] fila : filasProducto) {
                String nombreLower = fila[0].toString().toLowerCase();
                double monto = ((Number) fila[1]).doubleValue();
                if (nombreLower.contains("cámara") || nombreLower.contains("camara")) ventasCamara += monto;
                else if (nombreLower.contains("pie")) ventasPie += monto;
            }

            String sqlMermasHoy = "SELECT COALESCE(SUM(m.peso_perdido_kg * p.precio_kg), 0) " +
                    "FROM mermas m INNER JOIN productos p ON m.id_producto = p.id_producto " +
                    "WHERE DATE(m.fecha) = CURRENT_DATE()";
            Number mermasHoy = (Number) em.createNativeQuery(sqlMermasHoy).getSingleResult();

            ctx.status(200).json(Map.of(
                    "fondo_inicial", fondoInicial.doubleValue(),
                    "ventas_pie", ventasPie,
                    "ventas_camara", ventasCamara,
                    "mermas", mermasHoy.doubleValue()
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al calcular datos automáticos: " + e.getMessage()));
        }
    }

    // 8b. POST - El cajero GENERA y ENVÍA el reporte al dueño (con datos automáticos +
    // los que completó a mano). Ya NO descarga nada aquí — solo guarda el reporte y
    // notifica al dueño, quien lo ve y descarga desde su propia pantalla.
    public void generarYEnviarReporteEnVivo(Context ctx) {
        Map<?, ?> body = ctx.bodyAsClass(Map.class);
        EntityManager em = emf.createEntityManager();
        try {
            String sqlAbierta = "SELECT c.id_cierre, u.nombre, c.fecha_apertura FROM cierres_caja c " +
                    "INNER JOIN usuarios u ON c.id_usuario = u.id_usuario " +
                    "WHERE c.estado_cierre = 'Abierta' LIMIT 1";
            List<Object[]> activaRow = em.createNativeQuery(sqlAbierta).getResultList();
            if (activaRow.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No hay ninguna caja abierta ahora mismo."));
                return;
            }
            Object[] row = activaRow.get(0);
            Integer idCierre = (Integer) row[0];
            String cajero = (String) row[1];
            Object fechaApertura = row[2];

            double cajaInicio = Double.parseDouble(body.get("caja_inicio").toString());
            double cajaActual = Double.parseDouble(body.get("caja_actual").toString());
            double ventasPie = Double.parseDouble(body.get("ventas_pie").toString());
            double ventasCamara = Double.parseDouble(body.get("ventas_camara").toString());
            int apartadosWeb = body.get("apartados_web") != null ? Integer.parseInt(body.get("apartados_web").toString()) : 0;
            double mermas = Double.parseDouble(body.get("mermas").toString());
            String notas = body.get("notas") != null ? body.get("notas").toString() : "";

            String reporteTexto = "==================================================\n" +
                    "       AVISTOCK - REPORTE DE CAJA (GENERADO POR CAJERO)  \n" +
                    "==================================================\n" +
                    "FOLIO DE TURNO      : CR-" + idCierre + "\n" +
                    "CAJERO EN TURNO     : " + cajero + "\n" +
                    "ABIERTO DESDE       : " + fechaApertura + "\n" +
                    "--------------------------------------------------\n" +
                    "Dinero inicial      : $" + String.format("%.2f", cajaInicio) + "\n" +
                    "Dinero actual       : $" + String.format("%.2f", cajaActual) + "\n" +
                    "Ventas Pollo en Pie : $" + String.format("%.2f", ventasPie) + "\n" +
                    "Ventas Pollo Cámara : $" + String.format("%.2f", ventasCamara) + "\n" +
                    "Apartados web       : " + apartadosWeb + " ud\n" +
                    "Mermas              : $" + String.format("%.2f", mermas) + "\n" +
                    "--------------------------------------------------\n" +
                    "Observaciones: " + (notas.isBlank() ? "(ninguna)" : notas) + "\n" +
                    "==================================================\n" +
                    "  Reporte generado manualmente por el cajero —      \n" +
                    "  la caja NO se ha cerrado todavía.                 \n" +
                    "==================================================\n";

            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO notificaciones_caja (tipo, mensaje, id_cierre, leida, fecha_creacion, detalle_texto) VALUES ('REPORTE_ENVIVO', ?, ?, false, NOW(), ?)")
                    .setParameter(1, "El cajero " + cajero + " generó y envió un reporte del turno actual (Folio #CR-" + idCierre + ").")
                    .setParameter(2, idCierre)
                    .setParameter(3, reporteTexto)
                    .executeUpdate();
            em.getTransaction().commit();

            ctx.status(200).json(Map.of("success", true, "mensaje", "Reporte generado y enviado al dueño correctamente."));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(400).json(Map.of("error", "Error al generar el reporte: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // 8c. GET - El DUEÑO descarga el reporte exacto que le mandó el cajero (mismo texto
    // guardado en el momento en que se generó, no un recálculo nuevo).
    public void descargarReporteEnVivoGuardado(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        String idParam = ctx.pathParam("id");

        try (EntityManager em = emf.createEntityManager()) {
            List<Object> filas = em.createNativeQuery(
                    "SELECT detalle_texto FROM notificaciones_caja WHERE id_notificacion = ? AND tipo = 'REPORTE_ENVIVO'"
            ).setParameter(1, Integer.parseInt(idParam)).getResultList();

            if (filas.isEmpty() || filas.get(0) == null) {
                ctx.status(404).json(Map.of("error", "No se encontró ese reporte en vivo."));
                return;
            }

            ctx.contentType("text/plain");
            ctx.header("Content-Disposition", "attachment; filename=Reporte_EnVivo_" + idParam + ".txt");
            ctx.result(filas.get(0).toString().getBytes());
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al descargar el reporte: " + e.getMessage()));
        }
    }
}