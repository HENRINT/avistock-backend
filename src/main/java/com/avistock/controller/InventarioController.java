package com.avistock.controller;

import io.javalin.http.Context;
import com.avistock.model.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InventarioController {

    private final EntityManagerFactory emf;

    // Extrae el primer número decimal de textos como "2.50 kg" -> 2.50
    private static final Pattern NUMERO_PATTERN = Pattern.compile("[0-9]+([.,][0-9]+)?");

    public InventarioController(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /**
     * 0. POST /api/admin/inventario/producto
     * NUEVO: antes no existía ninguna forma de crear un producto desde cero.
     * Los demás endpoints (recepción, precio, merma) solo actualizan productos
     * que ya existen buscándolos por nombre ("pie"/"cámara"). Este endpoint
     * es el que realmente inserta la fila inicial en la tabla `productos`.
     */
    public void crearProducto(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);

            String nombre = body.get("nombre") != null ? body.get("nombre").toString().trim() : null;
            if (nombre == null || nombre.isBlank()) {
                ctx.status(400).json(Map.of("error", "El campo 'nombre' es obligatorio."));
                return;
            }

            Producto producto = new Producto();
            producto.setNombre(nombre);
            producto.setDescripcion(body.get("descripcion") != null ? body.get("descripcion").toString() : "");
            producto.setPrecioKg(body.get("precioKg") != null ? Double.parseDouble(body.get("precioKg").toString()) : 0);
            producto.setPrecioUnidad(body.get("precioUnidad") != null ? Double.parseDouble(body.get("precioUnidad").toString()) : 0);
            producto.setPesoAproximado(body.get("pesoAproximado") != null ? body.get("pesoAproximado").toString() : "0 kg");
            producto.setUnidadesDisponibles(body.get("unidadesDisponibles") != null ? Integer.parseInt(body.get("unidadesDisponibles").toString()) : 0);

            em.getTransaction().begin();
            em.persist(producto);
            em.getTransaction().commit();

            ctx.status(201).json(Map.of(
                    "status", "success",
                    "mensaje", "¡Producto creado correctamente!",
                    "idProducto", producto.getIdProducto()
            ));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(400).json(Map.of("error", "Error al crear producto: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    /**
     * 1. GET /api/admin/inventario
     */
    public void obtenerInventarioYResumen(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            List<Producto> productos = em.createQuery("SELECT p FROM Producto p", Producto.class).getResultList();

            List<Map<String, Object>> stockList = new ArrayList<>();
            for (Producto p : productos) {
                Map<String, Object> stockItem = new HashMap<>();
                stockItem.put("id", p.getIdProducto());
                stockItem.put("producto", p.getNombre());
                stockItem.put("stock", p.getUnidadesDisponibles() + " ud");
                stockItem.put("min", "20 ud");
                stockItem.put("precio", "$" + p.getPrecioUnidad());
                // NUEVO: antes solo se devolvía el precio por unidad; faltaba el precio por kg.
                stockItem.put("precio_kg", "$" + p.getPrecioKg());
                stockItem.put("precio_kg_num", p.getPrecioKg());
                stockItem.put("precio_num", p.getPrecioUnidad());
                stockList.add(stockItem);
            }

            // Mermas reales del mes actual, agrupadas por producto (antes venía hardcodeado en "0 ud" / "$0")
            @SuppressWarnings("unchecked")
            List<Object[]> rawMermas = em.createNativeQuery(
                    "SELECT p.id_producto, p.nombre, COALESCE(SUM(m.cantidad_aves), 0), " +
                    "COALESCE(SUM(m.peso_perdido_kg * p.precio_kg), 0) " +
                    "FROM productos p LEFT JOIN mermas m ON m.id_producto = p.id_producto " +
                    "AND MONTH(m.fecha) = MONTH(CURRENT_DATE()) AND YEAR(m.fecha) = YEAR(CURRENT_DATE()) " +
                    "GROUP BY p.id_producto, p.nombre"
            ).getResultList();

            List<Map<String, Object>> mermasList = new ArrayList<>();
            for (Object[] row : rawMermas) {
                Map<String, Object> mermaItem = new HashMap<>();
                mermaItem.put("id", row[0]);
                mermaItem.put("producto", row[1]);
                mermaItem.put("cantidad", row[2] + " ud");
                mermaItem.put("perdido", "$" + row[3]);
                mermasList.add(mermaItem);
            }

            ctx.status(200).json(Map.of(
                    "stock", stockList,
                    "mermas", mermasList
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al obtener inventario: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    /**
     * 2. POST /api/admin/inventario/recepcion
     *
     * LÓGICA DE NEGOCIO CORREGIDA: los pollos en cámara no llegan como un lote
     * aparte — son pollos en pie que ya tenías y que se procesan/refrigeran.
     * Por eso "pollosCamara" ya NO se suma de forma independiente: se resta
     * del stock de "Pollos en Pie" y se traslada a "Pollos en Cámara".
     * La única entrada real de aves nuevas al negocio es "pollosPie".
     */
    public void agregarRecepcion(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);

            int pollosCamara = Integer.parseInt(body.get("pollosCamara").toString());
            int pollosPie = Integer.parseInt(body.get("pollosPie").toString());

            // MEJORADO: antes había UN solo precio compartido entre pie y cámara — si
            // recibías ambos en la misma recepción, terminaban con el mismo precio, aunque
            // cámara normalmente cuesta más por el proceso. Ahora cada uno tiene su propio
            // precio por unidad y por kg, independientes entre sí.
            double precioPieUnidad = body.get("precioPieUnidad") != null ? Double.parseDouble(body.get("precioPieUnidad").toString()) : 0;
            double precioPieKg = body.get("precioPieKilo") != null ? Double.parseDouble(body.get("precioPieKilo").toString()) : 0;
            double precioCamaraUnidad = body.get("precioCamaraUnidad") != null ? Double.parseDouble(body.get("precioCamaraUnidad").toString()) : 0;
            double precioCamaraKg = body.get("precioCamaraKilo") != null ? Double.parseDouble(body.get("precioCamaraKilo").toString()) : 0;

            String pesoAprox = body.get("pesoAprox") != null ? body.get("pesoAprox").toString() : "2.50 kg";
            int mermasTransporte = Integer.parseInt(body.get("mermasTransporte").toString());

            double pesoUnitarioKg = extraerNumero(pesoAprox, 2.50);

            // Precio de compra/kg para el registro del lote: usa el precio/kg capturado de
            // cada producto; si no viene, lo estima dividiendo precio/unidad entre el peso.
            double precioCompraKiloPie = precioPieKg > 0 ? precioPieKg : ((precioPieUnidad > 0 && pesoUnitarioKg > 0) ? precioPieUnidad / pesoUnitarioKg : 0);
            double precioCompraKiloCamara = precioCamaraKg > 0 ? precioCamaraKg : ((precioCamaraUnidad > 0 && pesoUnitarioKg > 0) ? precioCamaraUnidad / pesoUnitarioKg : 0);

            em.getTransaction().begin();

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

            if (productoPie == null) {
                ctx.status(400).json(Map.of("error", "No existe un producto 'Pollo en Pie' registrado."));
                return;
            }

            String sqlInsertLote = "INSERT INTO inventario_lotes " +
                    "(id_producto, cantidad_inicial_aves, cantidad_actual_aves, peso_conjunto_total_kg, " +
                    "peso_individual_promedio_kg, precio_compra_kilo, fecha_ingreso) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURDATE())";

            // 1) ENTRADA REAL: pollos en pie que llegan del corral/proveedor (menos mermas de transporte)
            if (pollosPie > 0) {
                int pollosNetosEnPie = Math.max(0, pollosPie - mermasTransporte);
                productoPie.setUnidadesDisponibles(productoPie.getUnidadesDisponibles() + pollosNetosEnPie);
                productoPie.setPesoAproximado(pesoAprox);
                if (precioPieUnidad > 0) productoPie.setPrecioUnidad(precioPieUnidad);
                if (precioPieKg > 0) productoPie.setPrecioKg(precioPieKg);

                em.createNativeQuery(sqlInsertLote)
                        .setParameter(1, productoPie.getIdProducto())
                        .setParameter(2, pollosPie)
                        .setParameter(3, pollosNetosEnPie)
                        .setParameter(4, pollosNetosEnPie * pesoUnitarioKg)
                        .setParameter(5, pesoUnitarioKg)
                        .setParameter(6, precioCompraKiloPie)
                        .executeUpdate();
            }

            // 2) CONVERSIÓN: pollos que se procesan de Pie -> Cámara (se resta de uno, se suma al otro)
            if (pollosCamara > 0) {
                if (productoCamara == null) {
                    ctx.status(400).json(Map.of("error", "No existe un producto 'Pollo en Cámara' registrado."));
                    return;
                }
                if (productoPie.getUnidadesDisponibles() < pollosCamara) {
                    ctx.status(400).json(Map.of(
                            "error", "No hay suficientes pollos en pie para procesar a cámara. " +
                                    "Disponibles: " + productoPie.getUnidadesDisponibles() + ", solicitados: " + pollosCamara
                    ));
                    return;
                }

                productoPie.setUnidadesDisponibles(productoPie.getUnidadesDisponibles() - pollosCamara);
                productoCamara.setUnidadesDisponibles(productoCamara.getUnidadesDisponibles() + pollosCamara);
                if (precioCamaraUnidad > 0) productoCamara.setPrecioUnidad(precioCamaraUnidad);
                if (precioCamaraKg > 0) productoCamara.setPrecioKg(precioCamaraKg);
                productoCamara.setPesoAproximado(pesoAprox);

                em.createNativeQuery(sqlInsertLote)
                        .setParameter(1, productoCamara.getIdProducto())
                        .setParameter(2, pollosCamara)
                        .setParameter(3, pollosCamara)
                        .setParameter(4, pollosCamara * pesoUnitarioKg)
                        .setParameter(5, pesoUnitarioKg)
                        .setParameter(6, precioCompraKiloCamara)
                        .executeUpdate();
            }

            em.getTransaction().commit();
            ctx.status(200).json(Map.of("status", "success", "mensaje", "¡Recepción registrada e inventario actualizado!"));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(400).json(Map.of("error", "Error al procesar recepción: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    /**
     * 3. PATCH /api/admin/inventario/precio
     */
    public void actualizarPrecio(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Integer idProducto = Integer.parseInt(body.get("idProducto").toString());
            double nuevoPrecio = Double.parseDouble(body.get("nuevoPrecio").toString());
            // NUEVO: antes este endpoint solo aceptaba el precio por unidad; el precio por kg
            // se quedaba sin forma de actualizarse desde esta pantalla.
            Double nuevoPrecioKg = body.get("nuevoPrecioKg") != null ? Double.parseDouble(body.get("nuevoPrecioKg").toString()) : null;

            em.getTransaction().begin();
            Producto producto = em.find(Producto.class, idProducto);

            if (producto != null) {
                producto.setPrecioUnidad(nuevoPrecio);
                if (nuevoPrecioKg != null) producto.setPrecioKg(nuevoPrecioKg);
                em.getTransaction().commit();
                ctx.status(200).json(Map.of(
                        "status", "success",
                        "mensaje", "Precio actualizado: $" + nuevoPrecio + "/ud" + (nuevoPrecioKg != null ? " · $" + nuevoPrecioKg + "/kg" : "")
                ));
            } else {
                em.getTransaction().rollback();
                ctx.status(404).json(Map.of("error", "Producto no encontrado"));
            }
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(500).json(Map.of("error", "Error al actualizar precio: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    /**
     * 4. POST /api/admin/inventario/merma
     */
    public void registrarMerma(Context ctx) {
        EntityManager em = emf.createEntityManager();
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Integer idProducto = Integer.parseInt(body.get("idProducto").toString());
            int cantidadAJustar = Integer.parseInt(body.get("cantidad").toString());
            String motivo = body.get("motivo") != null ? body.get("motivo").toString() : "No especificado";
            String responsable = body.get("responsable") != null ? body.get("responsable").toString() : "No especificado";

            em.getTransaction().begin();
            Producto producto = em.find(Producto.class, idProducto);

            if (producto != null) {
                int stockActual = producto.getUnidadesDisponibles();
                producto.setUnidadesDisponibles(Math.max(0, stockActual - cantidadAJustar));

                double dineroPerdido = cantidadAJustar * producto.getPrecioUnidad();

                // SUPUESTO: el formulario de merma manda "cantidad" en piezas (unidades), pero la
                // tabla 'mermas' guarda peso_perdido_kg. Se calcula multiplicando por el peso
                // aproximado del producto (ej. "2.50 kg" -> 2.50). Si prefieres capturar el peso
                // real pesado en báscula en vez de estimarlo, agrega ese campo al formulario y
                // lo cambiamos aquí.
                double pesoUnitarioKg = extraerNumero(producto.getPesoAproximado(), 2.50);
                double pesoPerdidoKg = cantidadAJustar * pesoUnitarioKg;

                em.createNativeQuery(
                                "INSERT INTO mermas (id_producto, id_lote, cantidad_aves, descripcion, peso_perdido_kg, responsable, fecha) " +
                                "VALUES (?, NULL, ?, ?, ?, ?, CURDATE())")
                        .setParameter(1, idProducto)
                        .setParameter(2, cantidadAJustar)
                        .setParameter(3, motivo)
                        .setParameter(4, pesoPerdidoKg)
                        .setParameter(5, responsable)
                        .executeUpdate();

                em.getTransaction().commit();

                ctx.status(200).json(Map.of(
                        "status", "success",
                        "mensaje", "Merma aplicada correctamente",
                        "producto", producto.getNombre(),
                        "unidades_restadas", cantidadAJustar,
                        "peso_perdido_kg", pesoPerdidoKg,
                        "dinero_perdido", dineroPerdido,
                        "motivo", motivo
                ));
            } else {
                em.getTransaction().rollback();
                ctx.status(404).json(Map.of("error", "Producto no encontrado"));
            }
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(500).json(Map.of("error", "Error al registrar la merma: " + e.getMessage()));
        } finally {
            em.close();
        }
    }

    // Extrae el primer número de un texto tipo "2.50 kg"; si no encuentra nada, usa el valor por defecto.
    private double extraerNumero(String texto, double valorPorDefecto) {
        if (texto == null || texto.isBlank()) return valorPorDefecto;
        Matcher m = NUMERO_PATTERN.matcher(texto);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group().replace(",", "."));
            } catch (NumberFormatException e) {
                return valorPorDefecto;
            }
        }
        return valorPorDefecto;
    }
}
