# Guía rápida: subir Avistock a AWS

## 1. Base de datos
Opción simple (misma instancia): instala MySQL en tu EC2 igual que en tu máquina local.
Opción robusta (recomendada a futuro): usa Amazon RDS (MySQL administrado).

Actualiza tu `.env` en el servidor con los datos reales:
```
DB_USER=usuario_avistock
DB_PASSWORD=TU_PASSWORD_REAL
DB_URL=jdbc:mysql://TU_HOST_DB:3306/avistock_db?serverTimezone=UTC
```

## 2. Generar el .jar ejecutable del backend
En tu máquina (o directo en el servidor si tienes el código ahí):
```bash
./gradlew clean build
```
Esto genera un `.jar` en `build/libs/`. Cópialo a tu servidor (por `scp` o subiéndolo por git).

## 3. Correr el backend en el servidor
Prueba manual primero:
```bash
java -jar avistock-1.0-SNAPSHOT.jar
```
Debe imprimir lo mismo que ves en tu consola local ("Servidor Avistock corriendo...").

Para que NO se caiga si cierras la sesión SSH, créalo como servicio con `systemd`
(pídeme el archivo de servicio si llegas a esta parte y te lo preparo).

## 4. Abrir el puerto en AWS
En el **Security Group** de tu instancia EC2:
- Agrega una regla de entrada (Inbound) para el puerto **8080** (o el que definas),
  origen `0.0.0.0/0` si quieres que cualquiera lo alcance (o restringido si prefieres).

## 5. El frontend
Ya no necesitas editar `session.js` a mano — `API_BASE` ahora se detecte solo según
desde dónde se abra la página:
- Si subes las carpetas `html-global`/`js-global`/`css-global`/`assets-global` al
  mismo servidor (ej. sirviéndolas con nginx o Apache), va a usar automáticamente
  ese mismo host en el puerto 8080 para hablar con el backend.
- Si el frontend termina en un dominio/servicio DISTINTO al backend (ej. frontend en
  un hosting estático y backend en otra IP), edita la línea final de `API_BASE` en
  `session.js` con la URL fija de tu backend.

## 6. HTTPS (recomendado antes de compartir la URL con clientes reales)
Si vas a exponer esto de verdad al público (no solo para tu presentación), considera
poner un proxy con nginx + certificado SSL gratuito (Let's Encrypt) enfrente del
backend, en vez de dejarlo en HTTP plano en el puerto 8080.

## 7. Seguridad — ya resuelto en este cambio
Las contraseñas ahora se guardan con hash real (bcrypt), no en texto plano. Tus
cuentas existentes (Harumi, Dueña, cualquier cliente registrado) se migran solas
la próxima vez que inicien sesión — no necesitas correr ningún script ni resetear
contraseñas.
