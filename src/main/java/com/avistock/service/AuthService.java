package com.avistock.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.avistock.model.Cliente;
import com.avistock.model.Usuario;
import com.avistock.repository.ClienteRepository;
import com.avistock.repository.UsuarioRepository;
import com.avistock.util.JwtUtil;
import io.jsonwebtoken.Claims;
import java.util.Map;
import java.util.Optional;

public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;

    public AuthService(UsuarioRepository usuarioRepository, ClienteRepository clienteRepository) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
    }

    /**
     * PANTALLA 1: Login Inteligente (Dueño, Administrador y Clientes)
     *
     * NUEVO: las contraseñas se verifican con bcrypt. Para no romper las cuentas
     * que ya tenías guardadas en texto plano (de antes de este cambio), se detecta
     * automáticamente si la contraseña guardada YA es un hash bcrypt o si sigue en
     * texto plano. Si es texto plano y coincide, se acepta el login Y de una vez se
     * reemplaza en la base de datos por su versión con hash — así cada cuenta se
     * "migra" sola la primera vez que inicia sesión, sin que tengas que correr
     * ningún script ni se rompa nada de golpe.
     */
    public Object loginGeneral(String correo, String contrasena) {
        if (correo == null || contrasena == null) {
            throw new RuntimeException("Correo o contraseña incorrectos.");
        }

        String correoLimpio = correo.trim();

        // 1. Buscamos primero en el personal interno (Harumi / Henri)
        Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorCorreo(correoLimpio);
        if (usuarioOpt.isPresent()) {
            Usuario u = usuarioOpt.get();
            if (verificarYMigrarSiEsNecesario(contrasena, u.getContrasena(), hashNuevo -> {
                u.setContrasena(hashNuevo);
                usuarioRepository.actualizarContrasena(u.getIdUsuario(), hashNuevo);
            })) {
                // EXPLICACIÓN: el login ya no regresa solo el objeto Usuario — regresa
                // el usuario MÁS un par de tokens firmados por este servidor. El
                // frontend guarda el accessToken y lo manda en cada request futura
                // (Authorization: Bearer ...); ya no manda su propio id "a mano" en
                // el header X-User-Id, que cualquiera podía falsificar.
                String accessToken = JwtUtil.generarAccessToken(u.getIdUsuario(), u.getRol(), u.getCorreo(), "usuario");
                String refreshToken = JwtUtil.generarRefreshToken(u.getIdUsuario(), "usuario");
                return Map.of("usuario", u, "token", accessToken, "refreshToken", refreshToken);
            }
        }

        // 2. Si no es personal, buscamos en la tabla de clientes
        Optional<Cliente> clienteOpt = clienteRepository.buscarPorCorreo(correoLimpio);
        if (clienteOpt.isPresent()) {
            Cliente c = clienteOpt.get();
            if (verificarYMigrarSiEsNecesario(contrasena, c.getContrasena(), hashNuevo -> {
                c.setContrasena(hashNuevo);
                clienteRepository.actualizarContrasena(c.getIdCliente(), hashNuevo);
            })) {
                String accessToken = JwtUtil.generarAccessToken(c.getIdCliente(), "Cliente", c.getCorreo(), "cliente");
                String refreshToken = JwtUtil.generarRefreshToken(c.getIdCliente(), "cliente");
                return Map.of("usuario", c, "token", accessToken, "refreshToken", refreshToken);
            }
        }

        throw new RuntimeException("Correo o contraseña incorrectos.");
    }

    /**
     * NUEVO: permite pedir un accessToken nuevo usando el refreshToken guardado,
     * sin que el usuario tenga que volver a escribir su contraseña cada 2 horas.
     */
    public Map<String, Object> refrescarToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Falta el refresh token.");
        }
        Claims claims;
        try {
            claims = JwtUtil.validarToken(refreshToken);
        } catch (Exception e) {
            throw new RuntimeException("Refresh token inválido o expirado.");
        }
        if (!"refresh".equals(claims.get("tipo", String.class))) {
            throw new RuntimeException("Este no es un refresh token.");
        }

        int idCuenta = Integer.parseInt(claims.getSubject());
        String tipoCuenta = claims.get("tipoCuenta", String.class);

        if ("usuario".equals(tipoCuenta)) {
            Usuario u = usuarioRepository.buscarPorId(idCuenta)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
            String nuevoAccess = JwtUtil.generarAccessToken(u.getIdUsuario(), u.getRol(), u.getCorreo(), "usuario");
            return Map.of("token", nuevoAccess);
        } else {
            Cliente c = clienteRepository.buscarPorId(idCuenta)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado."));
            String nuevoAccess = JwtUtil.generarAccessToken(c.getIdCliente(), "Cliente", c.getCorreo(), "cliente");
            return Map.of("token", nuevoAccess);
        }
    }

    /**
     * Verifica la contraseña contra el valor guardado, sea hash bcrypt o texto plano
     * (legado). Si coincide en texto plano, la migra a hash automáticamente.
     */
    private boolean verificarYMigrarSiEsNecesario(String contrasenaIngresada, String contrasenaGuardada, java.util.function.Consumer<String> alMigrar) {
        if (contrasenaGuardada == null) return false;

        boolean pareceHashBcrypt = contrasenaGuardada.startsWith("$2a$")
                || contrasenaGuardada.startsWith("$2b$")
                || contrasenaGuardada.startsWith("$2y$");

        if (pareceHashBcrypt) {
            BCrypt.Result resultado = BCrypt.verifyer().verify(contrasenaIngresada.toCharArray(), contrasenaGuardada);
            return resultado.verified;
        }

        // Contraseña vieja en texto plano (de antes de este cambio)
        if (contrasenaGuardada.equals(contrasenaIngresada)) {
            String nuevoHash = BCrypt.withDefaults().hashToString(12, contrasenaIngresada.toCharArray());
            alMigrar.accept(nuevoHash);
            return true;
        }

        return false;
    }

    /**
     * PANTALLA 2: Registro de Clientes
     */
    public Cliente registrarCliente(Cliente cliente) {
        if (cliente == null) {
            throw new RuntimeException("Datos de cliente inválidos.");
        }

        if (clienteRepository.buscarPorTelefono(cliente.getTelefono()).isPresent()) {
            throw new RuntimeException("El número telefónico ya está registrado.");
        }

        if (cliente.getCorreo() != null && !cliente.getCorreo().trim().isEmpty()) {
            String correoLimpio = cliente.getCorreo().trim();
            if (clienteRepository.buscarPorCorreo(correoLimpio).isPresent()) {
                throw new RuntimeException("El correo electrónico ya está registrado.");
            }
        }

        // NUEVO: hashea la contraseña antes de guardarla — nunca se guarda en texto plano
        // para cuentas creadas desde ahora en adelante.
        if (cliente.getContrasena() != null && !cliente.getContrasena().isBlank()) {
            String hash = BCrypt.withDefaults().hashToString(12, cliente.getContrasena().toCharArray());
            cliente.setContrasena(hash);
        }

        return clienteRepository.guardar(cliente);
    }
}