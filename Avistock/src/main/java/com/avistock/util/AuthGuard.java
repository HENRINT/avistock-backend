package com.avistock.util;

import io.javalin.http.Context;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;

public class AuthGuard {

    // EXPLICACIÓN: se mantiene el parámetro `emf` en la firma aunque ya no se use
    // para consultar la BD — así los controladores que ya llaman a
    // AuthGuard.exigirRol(emf, ctx, ...) NO necesitan tocarse. El cambio real está
    // adentro: antes se confiaba en el header X-User-Id (falsificable con Postman,
    // cualquiera podía mandar "X-User-Id: 1" y hacerse pasar por el Dueño sin
    // loguearse jamás); ahora se exige un JWT firmado por este mismo servidor en el
    // header Authorization: Bearer <token>, así que el rol viene de un token que
    // solo el backend pudo haber firmado — no de lo que el cliente diga que es.
    public static boolean exigirRol(EntityManagerFactory emf, Context ctx, String... rolesPermitidos) {
        Claims claims = extraerClaims(ctx);
        if (claims == null) return false; // extraerClaims ya respondió el error al cliente

        String rolReal = claims.get("rol", String.class);
        if (rolReal == null) {
            ctx.status(403).json(Map.of("error", "El token no contiene un rol válido."));
            return false;
        }
        rolReal = rolReal.toLowerCase();

        for (String fragmento : rolesPermitidos) {
            if (rolReal.contains(fragmento.toLowerCase())) return true;
        }
        ctx.status(403).json(Map.of("error", "Tu rol (" + rolReal + ") no tiene permiso para acceder a este recurso."));
        return false;
    }

    // EXPLICACIÓN: expuesto aparte para endpoints que solo necesitan "estar logueado
    // con cualquier rol interno" (cajero o dueño), sin exigir un rol específico.
    public static Claims extraerClaims(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            ctx.status(401).json(Map.of("error", "No autenticado: falta el token Bearer."));
            return null;
        }
        String token = header.substring(7).trim();
        try {
            Claims claims = JwtUtil.validarToken(token);
            if (!"access".equals(claims.get("tipo", String.class))) {
                ctx.status(401).json(Map.of("error", "Tipo de token inválido para esta operación."));
                return null;
            }
            return claims;
        } catch (ExpiredJwtException e) {
            ctx.status(401).json(Map.of("error", "Sesión expirada, vuelve a iniciar sesión."));
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            ctx.status(401).json(Map.of("error", "Token inválido."));
            return null;
        }
    }
}
