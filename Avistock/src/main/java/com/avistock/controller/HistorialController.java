package com.avistock.controller;

import io.javalin.http.Context;
import com.avistock.util.AuthGuard;
import com.avistock.model.VentasMostrador;
import com.avistock.model.Apartado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class HistorialController {

    private final EntityManagerFactory emf;

    public HistorialController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /**
     * GET /api/admin/historial/ventas
     */
    public void obtenerHistorialVentasHoy(Context ctx) {
        // SEGURIDAD: antes este endpoint no exigia ningun rol ni token -
        // cualquiera con la URL, sin loguearse, podia leer o modificar estos
        // datos con curl/Postman. Ahora se exige un JWT valido de Administrador
        // o Dueno (ambos roles usan esta pantalla).
        if (!AuthGuard.exigirRol(emf, ctx, "administr", "dueñ", "dueno", "owner")) return;

        EntityManager em = emf.createEntityManager();
        try {
            LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
            LocalDateTime finDia = LocalDate.now().atTime(23, 59, 59);

            List<VentasMostrador> ventas = em.createQuery(
                            "SELECT DISTINCT v FROM VentasMostrador v LEFT JOIN FETCH v.detalles " +
                                    "WHERE v.fechaHora BETWEEN :inicio AND :fin ORDER BY v.fechaHora DESC",
                            VentasMostrador.class
                    )
                    .setParameter("inicio", inicioDia)
                    .setParameter("fin", finDia)
                    .getResultList();

            // Creamos la lista manualmente para evitar problemas de inferencia de tipos con streams
            List<Map<String, Object>> respuesta = new ArrayList<>();

            for (VentasMostrador v : ventas) {
                String productosResumen = v.getDetalles().stream()
                        .map(d -> d.getProducto() != null ? d.getProducto().getNombre() + " " + d.getPesoRealKg() + " kg" : "Producto " + d.getPesoRealKg() + " kg")
                        .collect(Collectors.joining(", "));

                String horaFormateada = "00:00 hrs";
                if (v.getFechaHora() != null) {
                    horaFormateada = v.getFechaHora().toLocalTime().toString().substring(0, 5) + " hrs";
                }

                Map<String, Object> fila = new HashMap<>();
                fila.put("ticket", "#" + v.getIdVenta());
                fila.put("hora", horaFormateada);
                fila.put("cliente", v.getClienteNombreManual() != null ? v.getClienteNombreManual() : "Público General");
                fila.put("tipo", v.getTipoVenta() != null ? v.getTipoVenta() : "Mostrador");
                fila.put("productos", productosResumen.isEmpty() ? "Sin productos" : productosResumen);
                fila.put("total", (Double) v.getTotalVenta()); // Casteo explícito preventivo
                fila.put("estado", "Pagado");

                respuesta.add(fila);
            }

            double totalDia = ventas.stream().mapToDouble(VentasMostrador::getTotalVenta).sum();

            Map<String, Object> jsonFinal = new HashMap<>();
            jsonFinal.put("ventas", respuesta);
            jsonFinal.put("totalDia", totalDia);

            ctx.status(200).json(jsonFinal);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al consultar historial: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    /**
     * GET /api/admin/historial/pedidos
     */
    public void obtenerHistorialPedidosHoy(Context ctx) {
        // SEGURIDAD: antes este endpoint no exigia ningun rol ni token -
        // cualquiera con la URL, sin loguearse, podia leer o modificar estos
        // datos con curl/Postman. Ahora se exige un JWT valido de Administrador
        // o Dueno (ambos roles usan esta pantalla).
        if (!AuthGuard.exigirRol(emf, ctx, "administr", "dueñ", "dueno", "owner")) return;

        EntityManager em = emf.createEntityManager();
        try {
            LocalDate hoy = LocalDate.now();

            List<Apartado> pedidos = em.createQuery(
                            "SELECT a FROM Apartado a JOIN FETCH a.cliente WHERE a.fechaRegistro = :hoy ORDER BY a.idApartado DESC",
                            Apartado.class
                    )
                    .setParameter("hoy", hoy)
                    .getResultList();

            List<Map<String, Object>> respuesta = new ArrayList<>();

            for (Apartado p : pedidos) {
                Map<String, Object> fila = new HashMap<>();
                fila.put("ticket", "#" + p.getIdApartado());
                fila.put("hora", "Web");
                fila.put("cliente", p.getNombreCompleto() != null ? p.getNombreCompleto() : "Cliente Desconocido");
                fila.put("tipo", "Pedido Web");
                fila.put("productos", "Apartado Global");
                fila.put("total", (Double) p.getTotalEstimado()); // Casteo explícito preventivo
                fila.put("estado", p.getEstado() != null ? p.getEstado() : "Pendiente");

                respuesta.add(fila);
            }

            ctx.status(200).json(respuesta);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al consultar pedidos web: " + e.getMessage()));
        } finally {
            em.close();
        }
    }
}