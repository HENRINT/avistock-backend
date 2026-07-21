package com.avistock.controller;

import io.javalin.http.Context;
import com.avistock.model.Apartado;
import com.avistock.model.Producto; // Importación del modelo Producto
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.util.Map;
import java.util.List;

public class ClienteApartadoController {

    private final EntityManagerFactory emf;

    // Constructor que recibe el emf inyectado desde el Main.java
    public ClienteApartadoController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // 1. GET: Obtener las existencias de productos para el catálogo del cliente
    public void obtenerProductos(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            List<Producto> productos = em.createQuery("FROM Producto", Producto.class).getResultList();
            ctx.status(200).json(productos);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error en el servidor al obtener productos: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // 2. POST: Procesar el formulario de reserva del cliente
    // CONTRATO JSON ESPERADO (plano):
    // { "idCliente": 5, "nombreCompleto": "...", "telefono": "...", "correo": "...",
    //   "horarioRecogida": "...", "totalEstimado": 385.00,
    //   "cantidadPie": 3, "cantidadCamara": 2 }
    // NOTA: se reescribió para NO usar ctx.bodyAsClass(Apartado.class) directo, porque
    // Apartado.cliente no tiene cascade configurado — un objeto "cliente": {"idCliente": X}
    // deserializado por Jackson es una instancia transitoria y Hibernate la rechaza al
    // hacer commit. Usamos em.getReference() para adjuntar una referencia manejada real.
    //
    // NUEVO: cantidadPie/cantidadCamara ahora se validan y descuentan del stock real,
    // igual que hace AdminVentasController con las ventas de mostrador. Antes un apartado
    // no tocaba el inventario en absoluto, así que se podía reservar más de lo que existía.
    @SuppressWarnings("unchecked")
    public void crearApartado(Context ctx) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);

            if (body.get("idCliente") == null) {
                ctx.status(400).json(Map.of("error", "El campo 'idCliente' es obligatorio. El cliente debe iniciar sesión antes de reservar."));
                return;
            }
            Integer idCliente = Integer.parseInt(body.get("idCliente").toString());

            int cantidadPie = body.get("cantidadPie") != null ? Integer.parseInt(body.get("cantidadPie").toString()) : 0;
            int cantidadCamara = body.get("cantidadCamara") != null ? Integer.parseInt(body.get("cantidadCamara").toString()) : 0;

            tx.begin();

            // Localiza los productos "Pie" y "Cámara" por nombre (mismo patrón que InventarioController)
            List<Producto> productos = em.createQuery("SELECT p FROM Producto p", Producto.class).getResultList();
            Producto productoPie = null;
            Producto productoCamara = null;
            for (Producto p : productos) {
                String nombreLower = p.getNombre().toLowerCase();
                if (nombreLower.contains("cámara") || nombreLower.contains("camara")) {
                    productoCamara = p;
                } else if (nombreLower.contains("pie")) {
                    productoPie = p;
                }
            }

            if (cantidadPie > 0) {
                if (productoPie == null || productoPie.getUnidadesDisponibles() < cantidadPie) {
                    int disponiblePie = productoPie != null ? productoPie.getUnidadesDisponibles() : 0;
                    int disponibleCamara = productoCamara != null ? productoCamara.getUnidadesDisponibles() : 0;
                    tx.rollback();
                    ctx.status(400).json(Map.of(
                            "error", "Stock insuficiente. Disponible: " + disponiblePie + " en Pie y " + disponibleCamara + " en Cámara.",
                            "stock_pie", disponiblePie,
                            "stock_camara", disponibleCamara
                    ));
                    return;
                }
            }
            if (cantidadCamara > 0) {
                if (productoCamara == null || productoCamara.getUnidadesDisponibles() < cantidadCamara) {
                    int disponiblePie = productoPie != null ? productoPie.getUnidadesDisponibles() : 0;
                    int disponibleCamara = productoCamara != null ? productoCamara.getUnidadesDisponibles() : 0;
                    tx.rollback();
                    ctx.status(400).json(Map.of(
                            "error", "Stock insuficiente. Disponible: " + disponiblePie + " en Pie y " + disponibleCamara + " en Cámara.",
                            "stock_pie", disponiblePie,
                            "stock_camara", disponibleCamara
                    ));
                    return;
                }
            }

            if (cantidadPie > 0) productoPie.setUnidadesDisponibles(productoPie.getUnidadesDisponibles() - cantidadPie);
            if (cantidadCamara > 0) productoCamara.setUnidadesDisponibles(productoCamara.getUnidadesDisponibles() - cantidadCamara);

            Apartado apartado = new Apartado();
            apartado.setCliente(em.getReference(com.avistock.model.Cliente.class, idCliente));
            apartado.setNombreCompleto(body.get("nombreCompleto") != null ? body.get("nombreCompleto").toString() : null);
            apartado.setTelefono(body.get("telefono") != null ? body.get("telefono").toString() : null);
            apartado.setCorreo(body.get("correo") != null ? body.get("correo").toString() : null);
            apartado.setHorarioRecogida(body.get("horarioRecogida") != null ? body.get("horarioRecogida").toString() : null);
            apartado.setTotalEstimado(body.get("totalEstimado") != null ? Double.parseDouble(body.get("totalEstimado").toString()) : 0);
            apartado.setEstado("Pendiente");

            em.persist(apartado);
            tx.commit();

            ctx.status(201).json(Map.of(
                    "status", "success",
                    "id_apartado", apartado.getIdApartado(),
                    "estado", apartado.getEstado(),
                    "fecha_registro", apartado.getFechaRegistro() != null ? apartado.getFechaRegistro().toString() : ""
            ));
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            ctx.status(500).json(Map.of("error", "Error en el servidor al crear apartado: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // 3. DELETE: Cancelar el apartado desde el ticket de éxito
    public void cancelarApartado(Context ctx) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            Long id = Long.parseLong(ctx.pathParam("id"));

            tx.begin();
            Apartado apartado = em.find(Apartado.class, id);

            if (apartado != null) {
                em.remove(apartado); // Elimina el registro de la BD
                tx.commit();
                ctx.status(200).json(Map.of("message", "Apartado cancelado exitosamente"));
            } else {
                if (tx.isActive()) tx.rollback(); // Cerramos transacción si no se encuentra
                ctx.status(404).json(Map.of("error", "No se encontró el apartado con el ID provisto"));
            }
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            ctx.status(500).json(Map.of("error", "Error en el servidor al cancelar: " + e.getMessage()));
        } finally {
            em.close();
        }
    }
}