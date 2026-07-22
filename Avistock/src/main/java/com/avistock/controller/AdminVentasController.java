package com.avistock.controller;

import io.javalin.http.Context;
import com.avistock.model.VentasMostrador;
import com.avistock.model.DetalleVenta;
import com.avistock.model.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminVentasController {

    private final EntityManagerFactory emf;

    public AdminVentasController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // GET: Listar las ventas de HOY (con sus detalles) para la pantalla de Ventas
    public void obtenerVentasHoy(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            java.time.LocalDateTime inicioDia = java.time.LocalDate.now().atStartOfDay();
            java.time.LocalDateTime finDia = java.time.LocalDate.now().atTime(23, 59, 59);

            List<VentasMostrador> ventas = em.createQuery(
                    "SELECT DISTINCT v FROM VentasMostrador v LEFT JOIN FETCH v.detalles " +
                    "WHERE v.fechaHora BETWEEN :inicio AND :fin ORDER BY v.fechaHora DESC",
                    VentasMostrador.class
            ).setParameter("inicio", inicioDia).setParameter("fin", finDia).getResultList();
            ctx.status(200).json(ventas);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al consultar base de datos: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // POST: Guardar venta con su respectiva cascada de detalles
    // CONTRATO JSON ESPERADO (plano, sin objetos anidados con IDs sueltos):
    // {
    //   "idUsuario": 1, "idCierre": 1, "idPedido": null,
    //   "clienteNombreManual": "Público General", "tipoVenta": "Mostrador",
    //   "detalles": [
    //     { "idProducto": 3, "cantidadAves": 2, "pesoRealKg": 5.4, "precioUnitarioAplicado": 45.0 }
    //   ]
    // }
    // NOTA: se reescribió para NO usar ctx.bodyAsClass(VentasMostrador.class) directo, porque
    // DetalleVenta.producto no tiene cascade configurado — si el frontend manda un objeto
    // "producto": {"idProducto": X} tal cual lo deserializa Jackson, Hibernate lo trata como una
    // instancia transitoria y lanza TransientObjectException al hacer commit. Usamos
    // em.getReference() para adjuntar una referencia manejada real al Producto existente.
    @SuppressWarnings("unchecked")
    public void registrarVenta(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);

            List<Map<String, Object>> detallesBody = (List<Map<String, Object>>) body.get("detalles");
            if (detallesBody == null || detallesBody.isEmpty()) {
                ctx.status(400).json(Map.of("error", "La venta debe incluir al menos un detalle en 'detalles'."));
                return;
            }

            VentasMostrador nuevaVenta = new VentasMostrador();
            if (body.get("idUsuario") != null) nuevaVenta.setIdUsuario(Integer.parseInt(body.get("idUsuario").toString()));
            if (body.get("idPedido") != null) nuevaVenta.setIdPedido(Integer.parseInt(body.get("idPedido").toString()));
            if (body.get("clienteNombreManual") != null) nuevaVenta.setClienteNombreManual(body.get("clienteNombreManual").toString());
            if (body.get("tipoVenta") != null) nuevaVenta.setTipoVenta(body.get("tipoVenta").toString());
            nuevaVenta.setFechaHora(LocalDateTime.now());

            // CRÍTICO: ventas_mostrador.id_cierre es NOT NULL en tu base de datos real.
            // El frontend no rastrea manualmente qué caja está abierta, así que se busca aquí
            // automáticamente. De paso, esto obliga la regla de negocio correcta: no se puede
            // vender sin una caja abierta.
            if (body.get("idCierre") != null) {
                nuevaVenta.setIdCierre(Integer.parseInt(body.get("idCierre").toString()));
            } else {
                List<Object> cierreAbierto = em.createNativeQuery(
                        "SELECT id_cierre FROM cierres_caja WHERE estado_cierre = 'Abierta' LIMIT 1"
                ).getResultList();
                if (cierreAbierto.isEmpty()) {
                    ctx.status(400).json(Map.of("error", "No hay ninguna caja abierta. Abre la caja antes de registrar una venta."));
                    return;
                }
                Integer idCierreAbierto = ((Number) cierreAbierto.get(0)).intValue();
                nuevaVenta.setIdCierre(idCierreAbierto);
            }

            em.getTransaction().begin();

            List<DetalleVenta> detalles = new ArrayList<>();
            double totalCalculado = 0;

            for (Map<String, Object> d : detallesBody) {
                if (d.get("idProducto") == null) {
                    throw new IllegalArgumentException("Cada detalle debe incluir 'idProducto'.");
                }
                Integer idProducto = Integer.parseInt(d.get("idProducto").toString());
                int cantidadAves = d.get("cantidadAves") != null ? Integer.parseInt(d.get("cantidadAves").toString()) : 0;

                // CORREGIDO: antes no se descontaba el stock del producto al vender.
                // Se usa em.find() (no getReference) porque necesitamos leer y modificar
                // unidadesDisponibles, no solo usarlo como referencia para la FK.
                Producto producto = em.find(Producto.class, idProducto);
                if (producto == null) {
                    throw new IllegalArgumentException("No existe el producto con id " + idProducto);
                }
                if (producto.getUnidadesDisponibles() < cantidadAves) {
                    throw new IllegalArgumentException(
                            "Stock insuficiente de '" + producto.getNombre() + "'. Disponible: " +
                            producto.getUnidadesDisponibles() + ", solicitado: " + cantidadAves
                    );
                }
                producto.setUnidadesDisponibles(producto.getUnidadesDisponibles() - cantidadAves);

                DetalleVenta detalle = new DetalleVenta();
                detalle.setProducto(producto);
                detalle.setVenta(nuevaVenta);
                detalle.setCantidadAves(cantidadAves);
                double pesoRealKg = d.get("pesoRealKg") != null ? Double.parseDouble(d.get("pesoRealKg").toString()) : 0;
                double precioUnitarioAplicado = d.get("precioUnitarioAplicado") != null ? Double.parseDouble(d.get("precioUnitarioAplicado").toString()) : 0;
                detalle.setPesoRealKg(pesoRealKg);
                detalle.setPrecioUnitarioAplicado(precioUnitarioAplicado);

                double sub = pesoRealKg * precioUnitarioAplicado;
                detalle.setSubtotal(sub);
                totalCalculado += sub;

                detalles.add(detalle);
            }

            nuevaVenta.setDetalles(detalles);
            nuevaVenta.setTotalVenta(totalCalculado);

            em.persist(nuevaVenta);
            em.getTransaction().commit();

            ctx.status(201).json(Map.of(
                    "status", "success",
                    "mensaje", "¡Registro de venta exitoso!",
                    "id_generado", nuevaVenta.getIdVenta(),
                    "total_venta", totalCalculado
            ));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(400).json(Map.of("error", "Fallo al insertar registro: " + e.getMessage()));
        } finally {
            em.close();
        }
    }
}