package com.avistock.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ventas_mostrador")
public class VentasMostrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Integer idVenta;

    @Column(name = "id_usuario") // O puedes mapearlo con @ManyToOne a tu entidad Usuario
    private Integer idUsuario;

    @Column(name = "id_cierre")
    private Integer idCierre;

    @Column(name = "id_pedido")
    private Integer idPedido;

    @Column(name = "cliente_nombre_manual", length = 150)
    private String clienteNombreManual = "Público General";

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "tipo_venta", length = 30)
    private String tipoVenta = "Mostrador";

    // CORRECCIÓN HIBERNATE 6: Se remueven precision y scale para evitar el error con tipo double
    @Column(name = "total_venta")
    private double totalVenta = 0.00;

    // Relación inversa para jalar los detalles automáticamente
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<DetalleVenta> detalles = new ArrayList<>();

    public VentasMostrador() {}

    @PrePersist
    protected void onCreate() {
        if (this.fechaHora == null) {
            this.fechaHora = LocalDateTime.now();
        }
    }

    // Getters y Setters
    public Integer getIdVenta() { return idVenta; }
    public void setIdVenta(Integer idVenta) { this.idVenta = idVenta; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public Integer getIdCierre() { return idCierre; }
    public void setIdCierre(Integer idCierre) { this.idCierre = idCierre; }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public String getClienteNombreManual() { return clienteNombreManual; }
    public void setClienteNombreManual(String clienteNombreManual) { this.clienteNombreManual = clienteNombreManual; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public String getTipoVenta() { return tipoVenta; }
    public void setTipoVenta(String tipoVenta) { this.tipoVenta = tipoVenta; }

    public double getTotalVenta() { return totalVenta; }
    public void setTotalVenta(double totalVenta) { this.totalVenta = totalVenta; }

    public List<DetalleVenta> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleVenta> detalles) { this.detalles = detalles; }
}