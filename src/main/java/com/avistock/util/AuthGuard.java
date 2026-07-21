package com.avistock.util;

import io.javalin.http.Context;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;

/**
 * Verificación de permisos por rol, del lado del servidor.
 *
 * CÓMO FUNCIONA: el frontend manda quién dice ser en el encabezado "X-User-Id"
 * (lo agrega automáticamente session.js en cada petición). Este helper NO confía
 * en ese dato a ciegas — busca ese id_usuario en la tabla `usuarios` de verdad y
 * revisa cuál es su `rol` REAL guardado en la base de datos. Si alguien intentara
 * llamar a un endpoint protegido mandando un id que no existe, o cuyo rol real no
 * es el permitido, se rechaza con 401/403 sin importar qué diga el frontend.
 *
 * LIMITACIÓN HONESTA: esto evita que alguien "mienta" sobre su rol, pero no evita
 * que alguien mande el ID de OTRA persona real (no hay token de sesión firmado que
 * pruebe la identidad). Para cerrar ese último hueco haría falta un sistema de
 * sesiones/tokens real (ej. JWT), que es un cambio más grande.
 */
public class AuthGuard {

    public static boolean exigirRol(EntityManagerFactory emf, Context ctx, String... fragmentosRolPermitido) {
        String idHeader = ctx.header("X-User-Id");
        if (idHeader == null || idHeader.isBlank()) {
            ctx.status(401).json(Map.of("error", "No autenticado: falta identificarse (X-User-Id)."));
            return false;
        }

        Integer idUsuario;
        try {
            idUsuario = Integer.parseInt(idHeader);
        } catch (NumberFormatException e) {
            ctx.status(401).json(Map.of("error", "Identificador de sesión inválido."));
            return false;
        }

        try (EntityManager em = emf.createEntityManager()) {
            @SuppressWarnings("unchecked")
            List<Object> filas = em.createNativeQuery("SELECT rol FROM usuarios WHERE id_usuario = ?")
                    .setParameter(1, idUsuario)
                    .getResultList();

            if (filas.isEmpty()) {
                ctx.status(403).json(Map.of("error", "Usuario no encontrado o sin permisos para este recurso."));
                return false;
            }

            String rolReal = filas.get(0) != null ? filas.get(0).toString().toLowerCase() : "";
            for (String fragmento : fragmentosRolPermitido) {
                if (rolReal.contains(fragmento.toLowerCase())) {
                    return true;
                }
            }

            ctx.status(403).json(Map.of("error", "Tu rol (" + rolReal + ") no tiene permiso para acceder a este recurso."));
            return false;
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al verificar permisos: " + e.getMessage()));
            return false;
        }
    }
}
