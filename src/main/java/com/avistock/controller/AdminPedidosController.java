package com.avistock.controller;

import io.javalin.http.Context;
import com.avistock.model.Apartado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;

public class AdminPedidosController {

    private final EntityManagerFactory emf;

    public AdminPedidosController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // 1. GET: Listar apartados con opción de filtrar por estado (?estado=Listo)
    public void obtenerPedidosClientes(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            String filtroEstado = ctx.queryParam("estado");
            List<Apartado> pedidos;

            if (filtroEstado != null && !filtroEstado.isBlank()) {
                pedidos = em.createQuery(
                        "SELECT a FROM Apartado a JOIN FETCH a.cliente WHERE a.estado = :est ORDER BY a.fechaRegistro DESC",
                        Apartado.class
                ).setParameter("est", filtroEstado).getResultList();
            } else {
                pedidos = em.createQuery(
                        "SELECT a FROM Apartado a JOIN FETCH a.cliente ORDER BY a.fechaRegistro DESC",
                        Apartado.class
                ).getResultList();
            }

            ctx.status(200).json(pedidos);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al obtener pedidos: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // 2. PATCH: Cambiar el estado del pedido (Aceptar -> En curso -> Listo)
    public void actualizarEstadoPedido(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            // Obtenemos el nuevo estado desde el cuerpo del JSON enviado por el Front (ej: {"estado": "En curso"})
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String nuevoEstado = (String) body.get("estado");

            if (nuevoEstado == null || nuevoEstado.isBlank()) {
                ctx.status(400).json(Map.of("error", "El campo 'estado' es obligatorio."));
                return;
            }

            em.getTransaction().begin();
            Apartado pedido = em.find(Apartado.class, id);

            if (pedido != null) {
                pedido.setEstado(nuevoEstado);
                em.getTransaction().commit();
                ctx.status(200).json(Map.of(
                        "mensaje", "Pedido #" + id + " pasó al estado: " + nuevoEstado,
                        "id", id,
                        "estado", nuevoEstado
                ));
            } else {
                em.getTransaction().rollback();
                ctx.status(404).json(Map.of("error", "No se encontró el pedido con Folio #" + id));
            }
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(500).json(Map.of("error", "Error al actualizar estado: " + e.getMessage()));
        } finally {
            em.close();
        }
    }
}