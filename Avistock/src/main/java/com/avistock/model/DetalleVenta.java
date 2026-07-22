package com.avistock.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "detalle_venta")
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_venta")
    private Integer idDetalleVenta;

    // Conexión foránea con la venta principal
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_venta", nullable = false)
    @JsonIgnore // Evita bucles infinitos al convertir a JSON
    private VentasMostrador venta;

    // Conexión foránea con el producto comercializado
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "cantidad_aves")
    private Integer cantidadAves;

    // CORRECCIÓN HIBERNATE 6: Se remueven precision y scale en los tipos double
    @Column(name = "peso_real_kg")
    private double pesoRealKg;

    // CORREGIDO: la columna real en tu base de datos se llama "precio_unitario_aplicado"
    // (español, NOT NULL, sin valor por defecto). Antes apuntaba a "precio_unitario_applied"
    // (inglés), un nombre que no existe en tu script original — Hibernate, al no reconocerlo,
    // había creado esa columna nueva por su cuenta (nullable), dejando la columna real
    // "precio_unitario_aplicado" sin ningún valor, lo que tronaba el INSERT por violar NOT NULL.
    @Column(name = "precio_unitario_aplicado")
    private double precioUnitarioAplicado;

    @Column(name = "subtotal")
    private double subtotal;

    public DetalleVenta() {}

    @PrePersist
    @PreUpdate
    protected void calcularSubtotal() {
        this.subtotal = this.pesoRealKg * this.precioUnitarioAplicado;
    }

    // Getters y Setters
    public Integer getIdDetalleVenta() { return idDetalleVenta; }
    public void setIdDetalleVenta(Integer idDetalleVenta) { this.idDetalleVenta = idDetalleVenta; }

    public VentasMostrador getVenta() { return venta; }
    public void setVenta(VentasMostrador venta) { this.venta = venta; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public Integer getCantidadAves() { return cantidadAves; }
    public void setCantidadAves(Integer cantidadAves) { this.cantidadAves = cantidadAves; }

    public double getPesoRealKg() { return pesoRealKg; }
    public void setPesoRealKg(double pesoRealKg) { this.pesoRealKg = pesoRealKg; }

    public double getPrecioUnitarioAplicado() { return precioUnitarioAplicado; }
    public void setPrecioUnitarioAplicado(double precioUnitarioAplicado) { this.precioUnitarioAplicado = precioUnitarioAplicado; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
}