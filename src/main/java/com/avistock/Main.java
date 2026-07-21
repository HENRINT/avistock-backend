package com.avistock;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.avistock.controller.AuthController;
import com.avistock.controller.DashboardController;
import com.avistock.controller.CajaReporteController;
import com.avistock.controller.EstadisticasController;
import com.avistock.controller.ClienteApartadoController;
import com.avistock.controller.AdminVentasController;
import com.avistock.controller.AdminPedidosController;
import com.avistock.controller.HistorialController;
import com.avistock.controller.InventarioController;
import com.avistock.repository.UsuarioRepository;
import com.avistock.repository.ClienteRepository;
import com.avistock.service.AuthService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import io.github.cdimascio.dotenv.Dotenv; // Requerido para leer el .env
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        // A. Intenta cargar el archivo .env de forma segura
        Dotenv dotenv;
        try {
            dotenv = Dotenv.load();
        } catch (Exception e) {
            System.err.println("❌ ERROR CRÍTICO: No se encontró el archivo .env en la raíz del proyecto.");
            System.err.println("Por favor, crea un archivo .env basado en el ejemplo antes de iniciar.");
            return;
        }

        // B. Valida que las variables requeridas existan dentro del .env
        if (dotenv.get("DB_URL") == null || dotenv.get("DB_USER") == null || dotenv.get("DB_PASSWORD") == null) {
            System.err.println("❌ ERROR CRÍTICO: Faltan variables obligatorias en el archivo .env (DB_URL, DB_USER o DB_PASSWORD).");
            return;
        }

        // Creamos un mapa de propiedades para sobreescribir dinámicamente el persistence.xml
        Map<String, String> propiedadesEnv = new HashMap<>();

        // Inyectamos tus credenciales exactas y limpias del archivo .env
        propiedadesEnv.put("jakarta.persistence.jdbc.url", dotenv.get("DB_URL"));
        propiedadesEnv.put("jakarta.persistence.jdbc.user", dotenv.get("DB_USER"));

        // PROTECCIÓN: Si la contraseña es null o vacía en el .env, le pasamos un texto vacío "" para que no truene
        String dbPassword = dotenv.get("DB_PASSWORD");
        propiedadesEnv.put("jakarta.persistence.jdbc.password", dbPassword != null ? dbPassword : "");

        // Garantiza que Hibernate actualice el esquema automáticamente sin importar
        // lo que tenga persistence.xml (esa línea manda sobre la del XML)
        propiedadesEnv.put("hibernate.hbm2ddl.auto", "update");

        // 1. Inicializamos la fábrica de conexiones inyectando las credenciales seguras del .env
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("avistock_db", propiedadesEnv);

        // 2. Inicializamos los repositorios inyectándoles el 'emf' como exigen sus constructores
        UsuarioRepository usuarioRepository = new UsuarioRepository(emf);
        ClienteRepository clienteRepository = new ClienteRepository(emf);

        // 3. Inicializamos el servicio de autenticación con sus dos dependencias
        AuthService authService = new AuthService(usuarioRepository, clienteRepository);

        // 4. Inicializamos las instancias de todos los controladores pasándoles las dependencias requeridas
        AuthController authController = new AuthController(authService);
        DashboardController dashboardController = new DashboardController(emf);
        CajaReporteController cajaReporteController = new CajaReporteController(emf);
        EstadisticasController estadisticasController = new EstadisticasController(emf);
        ClienteApartadoController clienteApartadoController = new ClienteApartadoController(emf);
        AdminVentasController adminVentasController = new AdminVentasController(emf);
        AdminPedidosController adminPedidosController = new AdminPedidosController(emf);
        HistorialController historialController = new HistorialController(emf);
        InventarioController inventarioController = new InventarioController(emf);

        // NUEVO: ObjectMapper con JavaTimeModule registrado, para que Jackson pueda
        // convertir a JSON los campos LocalDateTime/LocalDate (fechaHora, fechaRegistro, etc.)
        // sin esto, cualquier endpoint que devuelva una entidad con ese tipo de campo truena.
        // WRITE_DATES_AS_TIMESTAMPS se desactiva para que las fechas salgan como texto
        // ISO-8601 legible (ej. "2026-07-21T15:30:00") en vez de un array de números
        // (ej. [2026,7,21,15,30,0]), que es lo que espera `new Date(...)` en el frontend.
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 5. Inicializamos y arrancamos el servidor Javalin en el puerto 8080 (con tus reglas CORS intactas)
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.anyHost();
                });
            });
            config.jsonMapper(new JavalinJackson(objectMapper, false));
        }).start(8080);

        // 6. Mapeo de rutas utilizando referencias a métodos estandarizadas

        // --- CORTESÍA: Endpoint raíz para Health Check ---
        app.get("/", ctx -> ctx.status(200).json(Map.of(
                "status", "online",
                "message", "API de Avistock operando correctamente",
                "version", "1.0.0"
        )));

        // --- RUTAS DE AUTENTICACIÓN ---
        app.post("/api/auth/login", authController::login);
        app.post("/api/auth/registrar-cliente", authController::registrarCliente);
        app.post("/api/auth/logout", authController::logout);

        // --- RUTAS DEL MÓDULO DEL DUEÑO: DASHBOARD ---
        app.get("/api/owner/dashboard", dashboardController::getOwnerDashboardData);

        // --- RUTAS DEL MÓDULO DEL DUEÑO: REPORTE Y CIERRE DE CAJA ---
        app.get("/api/owner/caja/resumen", cajaReporteController::getCajaDashboard);
        app.get("/api/owner/caja/notificaciones/historial", cajaReporteController::obtenerHistorialNotificaciones);
        app.get("/api/owner/caja/todos", cajaReporteController::obtenerTodosLosCierres);
        app.get("/api/owner/caja/detalle/{id}", cajaReporteController::getDetalleCierre);
        app.post("/api/owner/caja/solicitar", cajaReporteController::solicitarCorteParcial);

        // NUEVO: estos 4 métodos ya existían en CajaReporteController pero no tenían
        // ruta asignada, así que el frontend no podía abrir/cerrar caja ni descargar el PDF.
        app.post("/api/owner/caja/abrir", cajaReporteController::abrirCajaTurno);
        app.post("/api/owner/caja/cerrar", cajaReporteController::cerrarCajaDefinitivo);
        app.get("/api/owner/caja/detalle/{id}/pdf", cajaReporteController::descargarReportePdf);
        app.get("/api/owner/caja/notificaciones", cajaReporteController::obtenerNotificacionesPendientes);
        app.post("/api/owner/caja/notificaciones/marcar-leidas", cajaReporteController::marcarNotificacionesDuenoLeidas);
        // NUEVO: flujo correcto de "Generar Reporte" — el cajero autollena+completa datos y
        // ENVÍA (no descarga), y el dueño ve/descarga el reporte exacto desde su propia pantalla.
        app.get("/api/admin/caja/reporte-parcial/datos", cajaReporteController::obtenerDatosAutomaticosReporte);
        app.post("/api/admin/caja/reporte-parcial/generar", cajaReporteController::generarYEnviarReporteEnVivo);
        app.get("/api/owner/caja/reporte-envivo/{id}/descargar", cajaReporteController::descargarReporteEnVivoGuardado);

        // --- RUTAS DEL MÓDULO DEL DUEÑO: ESTADÍSTICAS ---
        app.get("/api/owner/estadisticas", estadisticasController::getAnalyticsData);
        app.get("/api/owner/estadisticas/semanal", estadisticasController::getEstadisticasSemanales);

        // --- MÓDULO DE CLIENTES (SISTEMA DE APARTADOS Y PRODUCTOS) ---
        app.get("/api/client/productos", clienteApartadoController::obtenerProductos);
        app.post("/api/client/apartados", clienteApartadoController::crearApartado);
        app.delete("/api/client/apartados/{id}", clienteApartadoController::cancelarApartado);

        // --- MÓDULO DE ADMINISTRADOR / MOSTRADOR (VENTAS REALES) ---
        app.get("/api/admin/ventas", adminVentasController::obtenerVentasHoy);
        app.post("/api/admin/ventas", adminVentasController::registrarVenta);

        // --- MÓDULO DE ADMINISTRADOR / GESTIÓN DE PEDIDOS WEB ---
        app.get("/api/admin/pedidos", adminPedidosController::obtenerPedidosClientes);
        app.patch("/api/admin/pedidos/{id}/estado", adminPedidosController::actualizarEstadoPedido);

        // --- MÓDULO DE ADMINISTRADOR / HISTORIAL DE VENTAS Y PEDIDOS WEB ---
        app.get("/api/admin/historial/ventas", historialController::obtenerHistorialVentasHoy);
        app.get("/api/admin/historial/pedidos", historialController::obtenerHistorialPedidosHoy);

        // --- MÓDULO DE ADMINISTRADOR / INVENTARIO, RECEPCIONES Y MERMAS ---
        app.get("/api/admin/inventario", inventarioController::obtenerInventarioYResumen);
        app.post("/api/admin/inventario/producto", inventarioController::crearProducto);
        app.post("/api/admin/inventario/recepcion", inventarioController::agregarRecepcion);
        app.patch("/api/admin/inventario/precio", inventarioController::actualizarPrecio);
        app.post("/api/admin/inventario/merma", inventarioController::registrarMerma);

        System.out.println("🚀 Servidor Avistock corriendo con éxito en http://localhost:8080");
    }
}