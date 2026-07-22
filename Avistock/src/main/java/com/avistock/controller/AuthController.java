package com.avistock.controller;

import com.avistock.model.Cliente;
import com.avistock.service.AuthService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.Map;

public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Inicio de sesión inteligente para Dueño, Administrador y Clientes.
     */
    public void login(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            Object loginResult = authService.loginGeneral(body.get("correo"), body.get("contrasena"));
            ctx.status(HttpStatus.OK).json(loginResult);
        } catch (Exception e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Registro público para nuevos Clientes desde la plataforma.
     */
    public void registrarCliente(Context ctx) {
        try {
            Cliente nuevoCliente = ctx.bodyAsClass(Cliente.class);
            Cliente guardado = authService.registrarCliente(nuevoCliente);
            ctx.status(HttpStatus.CREATED).json(guardado);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cierre de sesión general y universal para invalidar la sesión HTTP en el servidor.
     */
    public void logout(Context ctx) {
        try {
            ctx.req().getSession().invalidate();
            ctx.status(HttpStatus.OK).json(Map.of(
                    "success", true,
                    "mensaje", "Sesión cerrada correctamente."
            ));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", e.getMessage()));
        }
    }
}