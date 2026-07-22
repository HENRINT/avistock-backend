package com.avistock.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "apartados")
public class Apartado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_apartado")
    private Long idApartado; // Este ID será el número de apartado automático (ej: #4681)

    // Relación directa con el Cliente que subiste
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    // Estos campos los llenará el Front si el beneficiario o teléfono de entrega es distinto
    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

    @Column(name = "correo", length = 100)
    private String correo;

    @Column(name = "horario_recogida", nullable = false, length = 500)
    private String horarioRecogida;

    @Column(name = "total_estimado", nullable = false)
    private double totalEstimado;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    // NUEVO: Columna para controlar las pestañas del Front (Pendiente, En curso, Listo)
    @Column(name = "estado", nullable = false, length = 30)
    private String estado = "Pendiente";

    public Apartado() {}

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDate.now();
    }

    // Getters y Setters
    public Long getIdApartado() { return idApartado; }
    public void setIdApartado(Long idApartado) { this.idApartado = idApartado; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getHorarioRecogida() { return horarioRecogida; }
    public void setHorarioRecogida(String horarioRecogida) { this.horarioRecogida = horarioRecogida; }

    public double getTotalEstimado() { return totalEstimado; }
    public void setTotalEstimado(double totalEstimado) { this.totalEstimado = totalEstimado; }

    public LocalDate getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDate fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    // NUEVO: Getter y Setter de Estado
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}