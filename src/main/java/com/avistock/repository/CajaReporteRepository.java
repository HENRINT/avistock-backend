package com.avistock.repository;

import com.avistock.model.CierreCaja;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

public class CajaReporteRepository {

    private final EntityManagerFactory emf;

    public CajaReporteRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // Guarda la apertura (INSERT)
    public CierreCaja guardar(CierreCaja cierre) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            if (cierre.getIdCierre() == null) {
                em.persist(cierre);
            } else {
                cierre = em.merge(cierre);
            }
            em.getTransaction().commit();
            return cierre;
        } finally {
            em.close();
        }
    }

    // Busca si existe un turno activo actualmente ('Abierta')
    public Optional<CierreCaja> buscarTurnoActivo() {
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<CierreCaja> query = em.createQuery(
                    "SELECT c FROM CierreCaja c WHERE c.estadoCierre = 'Abierta'", CierreCaja.class);
            query.setMaxResults(1);
            List<CierreCaja> resultados = query.getResultList();
            return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
        } finally {
            em.close();
        }
    }

    // Busca un cierre por su ID
    public Optional<CierreCaja> buscarPorId(Integer id) {
        EntityManager em = emf.createEntityManager();
        try {
            CierreCaja c = em.find(CierreCaja.class, id);
            return Optional.ofNullable(c);
        } finally {
            em.close();
        }
    }

    // Obtiene todos los cierres para el historial del Dueño
    public List<CierreCaja> obtenerTodos() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT c FROM CierreCaja c ORDER BY c.fechaApertura DESC", CierreCaja.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // NOTA: se removieron calcularVentasEfectivoActual() y calcularMermasHoy() porque
    // referenciaban entidades JPA "Venta" y "Merma" que no existen en el proyecto (no
    // tienen clase @Entity). CajaReporteController ya calcula esos totales correctamente
    // con SQL nativo contra las tablas reales "ventas" y "mermas".
}