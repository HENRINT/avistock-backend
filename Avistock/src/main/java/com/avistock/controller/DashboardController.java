package com.avistock.controller;

import com.avistock.util.AuthGuard;
import io.javalin.http.Context;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardController {

    private final EntityManagerFactory emf;

    // CONSTRUCTOR: Recibe la configuración limpia desde el Main
    public DashboardController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public void getOwnerDashboardData(Context ctx) {
        if (!AuthGuard.exigirRol(emf, ctx, "dueñ", "dueno", "owner")) return;
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("welcome_text", "¡Bienvenido de vuelta, Dueño!");
        responseData.put("page_title", "Consolidado Global Avistock");
        responseData.put("page_subtitle", "Monitoreo del rendimiento del negocio, ventas, lotes y mermas.");

        // El uso de try-with-resources abre y cierra automáticamente el 'em' sin necesidad de finally
        try (EntityManager em = emf.createEntityManager()) {

            // --- QUERIES DE LAS TARJETAS ---
            String sqlGanancia = "SELECT ((SELECT COALESCE(SUM(total_venta), 0) FROM ventas_mostrador WHERE MONTH(fecha_hora) = MONTH(CURRENT_DATE())) - (SELECT COALESCE(SUM(peso_conjunto_total_kg * precio_compra_kilo), 0) FROM inventario_lotes WHERE MONTH(fecha_ingreso) = MONTH(CURRENT_DATE())))";
            Number gananciaNetaRes = (Number) em.createNativeQuery(sqlGanancia).getSingleResult();
            double gananciaNeta = gananciaNetaRes != null ? gananciaNetaRes.doubleValue() : 0.0;

            String sqlAves = "SELECT COALESCE(SUM(cantidad_actual_aves), 0) FROM inventario_lotes";
            Number totalAvesRes = (Number) em.createNativeQuery(sqlAves).getSingleResult();
            int totalAves = totalAvesRes != null ? totalAvesRes.intValue() : 0;

            String sqlMermas = "SELECT COALESCE(SUM(peso_perdido_kg), 0) FROM mermas WHERE MONTH(fecha) = MONTH(CURRENT_DATE())";
            Number totalMermasRes = (Number) em.createNativeQuery(sqlMermas).getSingleResult();
            double totalMermasKg = totalMermasRes != null ? totalMermasRes.doubleValue() : 0.0;

            // NUEVO: mermas del mes en dinero (la tarjeta "Mermas registradas" del frontend usa formato $, no kg)
            String sqlMermasDinero = "SELECT COALESCE(SUM(m.peso_perdido_kg * p.precio_kg), 0) " +
                    "FROM mermas m INNER JOIN productos p ON m.id_producto = p.id_producto " +
                    "WHERE MONTH(m.fecha) = MONTH(CURRENT_DATE())";
            Number mermasDineroRes = (Number) em.createNativeQuery(sqlMermasDinero).getSingleResult();
            double mermasDineroMes = mermasDineroRes != null ? mermasDineroRes.doubleValue() : 0.0;
            responseData.put("mermas_dinero_mes", mermasDineroMes);

            // CORREGIDO: "pedidos_linea" nunca se llena; tu sistema real de pedidos es "apartados"
            // (usado por ClienteApartadoController / AdminPedidosController), con columna "estado".
            String sqlPedidos = "SELECT COUNT(*) FROM apartados WHERE estado = 'Pendiente'";
            Number pedidosPendientesRes = (Number) em.createNativeQuery(sqlPedidos).getSingleResult();
            int pedidosPendientes = pedidosPendientesRes != null ? pedidosPendientesRes.intValue() : 0;

            // NUEVO: total vendido hoy (campo dedicado, para la tarjeta "Ventas del día" del frontend)
            String sqlVentasHoy = "SELECT COALESCE(SUM(total_venta), 0) FROM ventas_mostrador WHERE DATE(fecha_hora) = CURRENT_DATE()";
            Number ventasHoyRes = (Number) em.createNativeQuery(sqlVentasHoy).getSingleResult();
            double ventasHoy = ventasHoyRes != null ? ventasHoyRes.doubleValue() : 0.0;
            responseData.put("ventas_hoy", ventasHoy);

            // Empaquetar Tarjetas
            List<Map<String, Object>> cards = new ArrayList<>();
            cards.add(Map.of("title", "Ganancia Neta Mens", "value", "$" + String.format("%.2f", gananciaNeta)));
            cards.add(Map.of("title", "Población de Aves", "value", totalAves + " uds"));
            cards.add(Map.of("title", "Mermas del Mes", "value", String.format("%.2f", totalMermasKg) + " kg"));
            cards.add(Map.of("title", "Pedidos por Validar", "value", pedidosPendientes + " disp"));
            responseData.put("summary_cards", cards);

            // --- QUERY DE LA TABLA ---
            String sqlTabla = "SELECT u.nombre, v.cliente_nombre_manual, v.tipo_venta, v.total_venta " +
                    "FROM ventas_mostrador v " +
                    "INNER JOIN usuarios u ON v.id_usuario = u.id_usuario " +
                    "ORDER BY v.fecha_hora DESC LIMIT 4";

            @SuppressWarnings("unchecked")
            List<Object[]> rawRows = em.createNativeQuery(sqlTabla).getResultList();
            List<Map<String, Object>> tableRows = new ArrayList<>();

            for (Object[] row : rawRows) {
                Map<String, Object> mapRow = new HashMap<>();
                mapRow.put("col1_bold", row[0] != null ? row[0].toString() : "");
                mapRow.put("col2", row[1] != null ? row[1].toString() : "Público General");
                mapRow.put("col3", row[2] != null ? row[2].toString() : "");
                mapRow.put("col4", row[3] != null ? "$" + row[3].toString() : "$0.00");
                tableRows.add(mapRow);
            }
            responseData.put("recent_activity_table", tableRows);

            // --- DATOS DE LA GRÁFICA ---
            responseData.put("chart_data", Map.of(
                    "labels", List.of("Ene", "Feb", "Mar", "Abr", "May", "Jun"),
                    "linea_naranja_ingresos", List.of(12000, 15000, 18000, 14000, 22000, gananciaNeta > 0 ? gananciaNeta : 5000),
                    "linea_azul_costos", List.of(8000, 9500, 11000, 10500, 13000, 4000)
            ));

            // Respuesta exitosa enviada dentro del try
            ctx.status(200).json(responseData);

        } catch (Exception e) {
            // Manejo de excepciones controlado en JSON
            ctx.status(500).json(Map.of("error", "Error en JPA/Base de datos: " + e.getMessage()));
        }
    }
}