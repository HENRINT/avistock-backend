package com.avistock.util;

import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    // EXPLICACIÓN: el secreto vive SOLO en el .env, nunca en el código. Si falta o
    // está vacío, el servidor debe negarse a arrancar (falla rápido y visible, en vez
    // de firmar tokens con una clave nula/predecible).
    private static final Dotenv dotenv = Dotenv.load();
    private static final String SECRET = dotenv.get("JWT_SECRET");
    private static final SecretKey CLAVE;

    static {
        if (SECRET == null || SECRET.length() < 32) {
            throw new IllegalStateException(
                    "❌ ERROR CRÍTICO: JWT_SECRET falta o es demasiado corto en el .env. " +
                            "Genera uno con: openssl rand -base64 64");
        }
        CLAVE = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    // EXPLICACIÓN: vida corta (2h) a propósito — si un token se filtra, la ventana
    // de abuso es pequeña. Para seguir trabajando sin volver a pedir contraseña,
    // el cliente usa el refreshToken contra /api/auth/refresh.
    private static final long ACCESS_MINUTOS = 120;
    private static final long REFRESH_DIAS = 7;

    public static String generarAccessToken(int idUsuario, String rol, String correo, String tipoCuenta) {
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + ACCESS_MINUTOS * 60 * 1000);
        return Jwts.builder()
                .claims(Map.of(
                        "idUsuario", idUsuario,
                        "rol", rol,
                        "correo", correo,
                        "tipoCuenta", tipoCuenta,   // "usuario" (interno) o "cliente"
                        "tipo", "access"
                ))
                .subject(String.valueOf(idUsuario))
                .issuedAt(ahora)
                .expiration(expira)
                .signWith(CLAVE)
                .compact();
    }

    public static String generarRefreshToken(int idUsuario, String tipoCuenta) {
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + REFRESH_DIAS * 24L * 60 * 60 * 1000);
        return Jwts.builder()
                .claims(Map.of("idUsuario", idUsuario, "tipoCuenta", tipoCuenta, "tipo", "refresh"))
                .subject(String.valueOf(idUsuario))
                .issuedAt(ahora)
                .expiration(expira)
                .signWith(CLAVE)
                .compact();
    }

    // EXPLICACIÓN: si el token fue alterado, mal firmado o ya expiró, esto lanza
    // JwtException/ExpiredJwtException — AuthGuard lo atrapa y responde 401.
    public static Claims validarToken(String token) {
        return Jwts.parser()
                .verifyWith(CLAVE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
