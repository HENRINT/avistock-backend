package com.avistock.repository;

import com.avistock.model.Apartado;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

public class ApartadoRepository {

    private final EntityManagerFactory emf;

    public ApartadoRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public Apartado guardar(Apartado apartado) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(apartado);
            em.getTransaction().commit();
            return apartado;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw new RuntimeException("Error al guardar apartado: " + e.getMessage());
        } finally {
            em.close();
        }
    }
}
