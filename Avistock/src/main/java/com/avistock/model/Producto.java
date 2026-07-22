package com.avistock.model;

import jakarta.persistence.*;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    @Column(nullable = false, length = 100)
    private String nombre;

    // NOTA: se preserva el nombre físico de columna "Riveradescripcion" con @Column(name=...)
    // porque tu base de datos ya la creó así (hibernate.hbm2ddl.auto=update). Si renombraras
    // el atributo sin fijar el nombre de columna, Hibernate crearía una columna NUEVA vacía
    // en vez de usar la que ya tiene tus datos.
    @Column(name = "Riveradescripcion", nullable = false, length = 255)
    private String descripcion;

    @Column(name = "precio_kg", nullable = false)
    private double precioKg;

    @Column(name = "precio_unidad", nullable = false)
    private double precioUnidad;

    @Column(name = "peso_aproximado", length = 50)
    private String pesoAproximado;

    @Column(name = "unidades_disponibles", nullable = false)
    private int unidadesDisponibles;

    // ================================================================
    // COLUMNAS "HUÉRFANAS" DE TU SCRIPT SQL ORIGINAL: nombre_producto y
    // precio_base_kilo son NOT NULL sin valor por defecto, pero ningún
    // endpoint de la app las llena directamente (usan nombre/precioKg).
    // En vez de depender de que corras un ALTER TABLE manual, se
    // mapean aquí y se autocompletan en @PrePersist/@PreUpdate para
    // que el INSERT/UPDATE de Hibernate siempre las incluya con un
    // valor válido, sin tocar la base de datos.
    // ================================================================
    @Column(name = "nombre_producto")
    private String nombreProducto;

    @Column(name = "precio_base_kilo")
    private java.math.BigDecimal precioBaseKilo;

    @PrePersist
    @PreUpdate
    protected void sincronizarColumnasLegacy() {
        this.nombreProducto = this.nombre;
        this.precioBaseKilo = java.math.BigDecimal.valueOf(this.precioKg);
    }

    public Producto() {}

    // Getters y Setters corregidos a Integer
    public Integer getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(Integer idProducto) {
        this.idProducto = idProducto;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public double getPrecioKg() {
        return precioKg;
    }

    public void setPrecioKg(double precioKg) {
        this.precioKg = precioKg;
    }

    public double getPrecioUnidad() {
        return precioUnidad;
    }

    public void setPrecioUnidad(double precioUnidad) {
        this.precioUnidad = precioUnidad;
    }

    public String getPesoAproximado() {
        return pesoAproximado;
    }

    public void setPesoAproximado(String pesoAproximado) {
        this.pesoAproximado = pesoAproximado;
    }

    public int getUnidadesDisponibles() {
        return unidadesDisponibles;
    }

    public void setUnidadesDisponibles(int unidadesDisponibles) {
        this.unidadesDisponibles = unidadesDisponibles;
    }
}