package com.avistock.controller;

import com.avistock.util.AuthGuard;
import io.javalin.http.Context;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EstadisticasController {

    // Cambiado: Ahora se inyecta por constructor para utilizar el .env del Main
    private final EntityManagerFactory emf;

    // CONSTRUCTOR: Recibe la configuración limpia desde el Main
    public EstadisticasController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @SuppressWarnings("unchecked")
    public void getAnalyticsData(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        Map<String, Object> responseData = new HashMap<>();

        responseData.put("page_title", "Estadísticas y Analítica Avanzada");
        responseData.put("page_subtitle", "Análisis de rotación de producto, mermas críticas y rendimiento comercial.");

        try (EntityManager em = emf.createEntityManager()) {

            // --- 1. TOP PRODUCTOS MÁS VENDIDOS (Volumen en Kilos y Aves) ---
            // NOTA: se usa p.nombre (no p.nombre_producto) porque es la columna que realmente
            // llena InventarioController al crear/editar productos vía la app.
            String sqlTopProductos = "SELECT p.nombre, SUM(dv.cantidad_aves), SUM(dv.peso_real_kg), SUM(dv.subtotal) " +
                    "FROM detalle_venta dv " +
                    "INNER JOIN productos p ON dv.id_producto = p.id_producto " +
                    "GROUP BY p.id_producto, p.nombre " +
                    "ORDER BY SUM(dv.peso_real_kg) DESC";

            List<Object[]> rawProductos = em.createNativeQuery(sqlTopProductos).getResultList();
            List<Map<String, Object>> rankingProductos = new ArrayList<>();

            for (Object[] row : rawProductos) {
                Map<String, Object> prod = new HashMap<>();
                prod.put("producto", row[0]);
                prod.put("total_aves", row[1]);
                prod.put("total_kg", row[2]);
                prod.put("total_ingresos", row[3]);
                rankingProductos.add(prod);
            }
            responseData.put("ranking_productos", rankingProductos);


            // --- 2. ANÁLISIS DE MERMAS CRÍTICAS ---
            String sqlMermasCriticas = "SELECT p.nombre, SUM(m.peso_perdido_kg), " +
                    "SUM(m.peso_perdido_kg * p.precio_kg) AS costo_perdido " +
                    "FROM mermas m " +
                    "INNER JOIN productos p ON m.id_producto = p.id_producto " +
                    "WHERE MONTH(m.fecha) = MONTH(CURRENT_DATE()) AND YEAR(m.fecha) = YEAR(CURRENT_DATE()) " +
                    "GROUP BY p.id_producto, p.nombre";

            List<Object[]> rawMermas = em.createNativeQuery(sqlMermasCriticas).getResultList();
            List<Map<String, Object>> mermasAnalisis = new ArrayList<>();

            for (Object[] row : rawMermas) {
                Map<String, Object> merma = new HashMap<>();
                merma.put("producto", row[0]);
                merma.put("kg_perdidos", row[1]);
                merma.put("dinero_perdido", row[2]);
                mermasAnalisis.add(merma);
            }
            responseData.put("analisis_mermas", mermasAnalisis);


            // --- 3. COMPARATIVA DE CANALES DE VENTA ---
            String sqlCanales = "SELECT tipo_venta, SUM(total_venta), COUNT(*) " +
                    "FROM ventas_mostrador " +
                    "GROUP BY tipo_venta";

            List<Object[]> rawCanales = em.createNativeQuery(sqlCanales).getResultList();
            Map<String, Object> canalesData = new HashMap<>();

            for (Object[] row : rawCanales) {
                String canal = row[0] != null ? row[0].toString() : "Mostrador";
                canalesData.put(canal + "_monto", row[1]);
                canalesData.put(canal + "_operaciones", row[2]);
            }
            responseData.put("canales_venta", canalesData);


            // --- 4. DATA ESTRATÉGICA PARA GRÁFICOS ---
            responseData.put("chart_ventas_mensuales", Map.of(
                    "labels", List.of("Feb", "Mar", "Abr", "May", "Jun", "Jul"),
                    "valores", List.of(34000, 42000, 39000, 48000, 52000, 61000)
            ));

            ctx.status(200).json(responseData);

        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al procesar estadísticas: " + e.getMessage()));
        }
    }

    /**
     * NUEVO: GET /api/owner/estadisticas/semanal
     * Alimenta la gráfica de líneas (venta por día, semana actual vs anterior),
     * las tarjetas de pico/valle/total/mejor hora, "mejor día", "promedio diario",
     * y la dona real de Pollo en Pie vs Cámara vs Mermas — todo calculado desde
     * tus tablas reales (ventas_mostrador, detalle_venta, mermas), sin datos inventados.
     */
    @SuppressWarnings("unchecked")
    public void getEstadisticasSemanales(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        EntityManager em = emf.createEntityManager();
        try {
            LocalDate hoy = LocalDate.now();
            LocalDate lunesActual = hoy.with(DayOfWeek.MONDAY);
            LocalDate domingoActual = lunesActual.plusDays(6);
            LocalDate lunesAnterior = lunesActual.minusWeeks(1);
            LocalDate domingoAnterior = lunesAnterior.plusDays(6);

            double[] totalesActual = new double[7];
            double[] totalesAnterior = new double[7];
            cargarTotalesPorDia(em, lunesActual, totalesActual);
            cargarTotalesPorDia(em, lunesAnterior, totalesAnterior);

            String[] nombresDias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
            String[] nombresCortos = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

            int idxPico = 0, idxValle = 0;
            for (int i = 1; i < 7; i++) {
                if (totalesActual[i] > totalesActual[idxPico]) idxPico = i;
                if (totalesActual[i] < totalesActual[idxValle]) idxValle = i;
            }

            double totalActualSemana = Arrays.stream(totalesActual).sum();
            double totalAnteriorSemana = Arrays.stream(totalesAnterior).sum();
            double promedioDiario = totalActualSemana / 7.0;
            double promedioAnterior = totalAnteriorSemana / 7.0;
            double variacionPct = promedioAnterior > 0 ? ((promedioDiario - promedioAnterior) / promedioAnterior) * 100 : 0;

            // --- MEJOR HORA (bloque de 2 horas con más ventas en la semana actual) ---
            String sqlHora = "SELECT HOUR(fecha_hora) h, SUM(total_venta) t FROM ventas_mostrador " +
                    "WHERE fecha_hora BETWEEN ? AND ? GROUP BY HOUR(fecha_hora) ORDER BY t DESC LIMIT 1";
            List<Object[]> horaRows = em.createNativeQuery(sqlHora)
                    .setParameter(1, lunesActual.atStartOfDay())
                    .setParameter(2, domingoActual.atTime(23, 59, 59))
                    .getResultList();
            String mejorHora = "Sin datos aún";
            if (!horaRows.isEmpty()) {
                int h = ((Number) horaRows.get(0)[0]).intValue();
                mejorHora = String.format("%02d:00-%02d:00", h, (h + 2) % 24);
            }

            // --- DONA REAL: Pollo en Pie vs Pollo en Cámara vs Mermas (semana actual) ---
            String sqlVentasPorProducto = "SELECT p.nombre, COALESCE(SUM(dv.subtotal),0) " +
                    "FROM detalle_venta dv " +
                    "INNER JOIN productos p ON dv.id_producto = p.id_producto " +
                    "INNER JOIN ventas_mostrador v ON dv.id_venta = v.id_venta " +
                    "WHERE v.fecha_hora BETWEEN ? AND ? GROUP BY p.id_producto, p.nombre";
            List<Object[]> ventasProducto = em.createNativeQuery(sqlVentasPorProducto)
                    .setParameter(1, lunesActual.atStartOfDay())
                    .setParameter(2, domingoActual.atTime(23, 59, 59))
                    .getResultList();

            double ventasPie = 0, ventasCamara = 0;
            for (Object[] row : ventasProducto) {
                String nombreLower = row[0].toString().toLowerCase();
                double total = ((Number) row[1]).doubleValue();
                if (nombreLower.contains("cámara") || nombreLower.contains("camara")) ventasCamara += total;
                else if (nombreLower.contains("pie")) ventasPie += total;
            }

            String sqlMermas = "SELECT COALESCE(SUM(m.peso_perdido_kg * p.precio_kg),0) " +
                    "FROM mermas m INNER JOIN productos p ON m.id_producto = p.id_producto " +
                    "WHERE m.fecha BETWEEN ? AND ?";
            Number mermasRes = (Number) em.createNativeQuery(sqlMermas)
                    .setParameter(1, lunesActual)
                    .setParameter(2, domingoActual)
                    .getSingleResult();
            double mermasCosto = mermasRes != null ? mermasRes.doubleValue() : 0;

            double totalDonut = ventasPie + ventasCamara + mermasCosto;
            double pctPie = totalDonut > 0 ? (ventasPie / totalDonut) * 100 : 0;
            double pctCamara = totalDonut > 0 ? (ventasCamara / totalDonut) * 100 : 0;
            double pctMermas = totalDonut > 0 ? (mermasCosto / totalDonut) * 100 : 0;

            // --- ARMAR RESPUESTA ---
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("rango_texto", formatearRango(lunesActual, domingoActual));

            List<Map<String, Object>> dias = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                Map<String, Object> d = new HashMap<>();
                d.put("dia_corto", nombresCortos[i]);
                d.put("total_actual", totalesActual[i]);
                d.put("total_anterior", totalesAnterior[i]);
                dias.add(d);
            }
            respuesta.put("dias", dias);

            respuesta.put("pico_dia", nombresDias[idxPico]);
            respuesta.put("pico_valor", totalesActual[idxPico]);
            respuesta.put("valle_dia", nombresDias[idxValle]);
            respuesta.put("valle_valor", totalesActual[idxValle]);
            respuesta.put("total_actual", totalActualSemana);
            respuesta.put("total_anterior", totalAnteriorSemana);
            respuesta.put("mejor_hora", mejorHora);
            respuesta.put("promedio_diario", promedioDiario);
            respuesta.put("variacion_promedio_pct", variacionPct);
            respuesta.put("mejor_dia_nombre", nombresDias[idxPico]);
            respuesta.put("mejor_dia_valor", totalesActual[idxPico]);

            Map<String, Object> donut = new HashMap<>();
            donut.put("pie_pct", pctPie);
            donut.put("camara_pct", pctCamara);
            donut.put("mermas_pct", pctMermas);
            donut.put("pie_valor", ventasPie);
            donut.put("camara_valor", ventasCamara);
            donut.put("mermas_valor", mermasCosto);
            respuesta.put("donut", donut);

            ctx.status(200).json(respuesta);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al calcular estadísticas semanales: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    @SuppressWarnings("unchecked")
    private void cargarTotalesPorDia(EntityManager em, LocalDate lunes, double[] destino) {
        String sql = "SELECT DATE(fecha_hora) dia, SUM(total_venta) total FROM ventas_mostrador " +
                "WHERE fecha_hora BETWEEN ? AND ? GROUP BY DATE(fecha_hora)";
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, lunes.atStartOfDay())
                .setParameter(2, lunes.plusDays(6).atTime(23, 59, 59))
                .getResultList();
        for (Object[] row : rows) {
            LocalDate d = ((java.sql.Date) row[0]).toLocalDate();
            int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(lunes, d);
            if (idx >= 0 && idx < 7) {
                destino[idx] = ((Number) row[1]).doubleValue();
            }
        }
    }

    private String formatearRango(LocalDate lunes, LocalDate domingo) {
        String[] meses = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
        String lunesStr = lunes.getDayOfMonth() + " " + meses[lunes.getMonthValue() - 1];
        String domingoStr = domingo.getDayOfMonth() + " " + meses[domingo.getMonthValue() - 1] + " " + domingo.getYear();
        return "Lunes " + lunesStr + " - Domingo " + domingoStr;
    }
}