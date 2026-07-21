package com.avistock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cierres_caja")
public class CierreCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cierre")
    private Integer idCierre;

    @Column(name = "id_usuario")
    private Integer idUsuario; // Administrador o cajero que opera el turno

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fondo_inicial", precision = 10, scale = 2)
    private BigDecimal fondoInicial;

    @Column(name = "notas_apertura", length = 255)
    private String notasApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "total_sold_efectivo", precision = 10, scale = 2)
    private BigDecimal totalSoldEfectivo;

    @Column(name = "total_mermas_dia", precision = 10, scale = 2)
    private BigDecimal totalMermasDia;

    @Column(name = "efectivo_real_contado", precision = 10, scale = 2)
    private BigDecimal efectivoRealContado;

    @Column(name = "discrepancia", precision = 10, scale = 2)
    private BigDecimal discrepancia;

    @Column(name = "estado_cierre", length = 30)
    private String estadoCierre = "Abierta";

    @Column(name = "notas_cierre", length = 255)
    private String notasCierre;

    // --- CONSTRUCTORES ---
    public CierreCaja() {}

    // --- GETTERS Y SETTERS ---
    public Integer getIdCierre() { return idCierre; }
    public void setIdCierre(Integer idCierre) { this.idCierre = idCierre; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }

    public BigDecimal getFondoInicial() { return fondoInicial; }
    public void setFondoInicial(BigDecimal fondoInicial) { this.fondoInicial = fondoInicial; }

    public String getNotasApertura() { return notasApertura; }
    public void setNotasApertura(String notasApertura) { this.notasApertura = notasApertura; }

    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public BigDecimal getTotalSoldEfectivo() { return totalSoldEfectivo; }
    public void setTotalSoldEfectivo(BigDecimal totalSoldEfectivo) { this.totalSoldEfectivo = totalSoldEfectivo; }

    public BigDecimal getTotalMermasDia() { return totalMermasDia; }
    public void setTotalMermasDia(BigDecimal totalMermasDia) { this.totalMermasDia = totalMermasDia; }

    public BigDecimal getEfectivoRealContado() { return efectivoRealContado; }
    public void setEfectivoRealContado(BigDecimal efectivoRealContado) { this.efectivoRealContado = efectivoRealContado; }

    public BigDecimal getDiscrepancia() { return discrepancia; }
    public void setDiscrepancia(BigDecimal discrepancia) { this.discrepancia = discrepancia; }

    public String getEstadoCierre() { return estadoCierre; }
    public void setEstadoCierre(String estadoCierre) { this.estadoCierre = estadoCierre; }

    public String getNotasCierre() { return notasCierre; }
    public void setNotasCierre(String notasCierre) { this.notasCierre = notasCierre; }
}