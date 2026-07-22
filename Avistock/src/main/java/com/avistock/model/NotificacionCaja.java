package com.avistock.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones_caja")
public class NotificacionCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Integer idNotificacion;

    // Valores usados: 'CORTE_SOLICITADO' (dueño -> cajero),
    // 'CIERRE_DISPONIBLE' (cajero -> dueño), 'REPORTE_ENVIVO' (cajero -> dueño)
    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, length = 255)
    private String mensaje;

    @Column(name = "id_cierre")
    private Integer idCierre;

    @Column(nullable = false)
    private boolean leida = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    // NUEVO: guarda el texto completo del reporte tal cual lo generó el cajero (con sus
    // datos automáticos + manuales), para que el dueño vea EXACTAMENTE lo que se envió,
    // en vez de recalcular números en vivo en otro momento (que podrían ya ser distintos).
    @Lob
    @Column(name = "detalle_texto", columnDefinition = "TEXT")
    private String detalleTexto;

    public NotificacionCaja() {}

    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
    }

    // Getters y Setters
    public Integer getIdNotificacion() { return idNotificacion; }
    public void setIdNotificacion(Integer idNotificacion) { this.idNotificacion = idNotificacion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public Integer getIdCierre() { return idCierre; }
    public void setIdCierre(Integer idCierre) { this.idCierre = idCierre; }

    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getDetalleTexto() { return detalleTexto; }
    public void setDetalleTexto(String detalleTexto) { this.detalleTexto = detalleTexto; }
}
