package com.avistock.repository;

import com.avistock.model.Cliente;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import java.util.Optional;

public class ClienteRepository {

    private final EntityManagerFactory emf;

    public ClienteRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public Cliente guardar(Cliente cliente) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(cliente);
            em.getTransaction().commit();
            return cliente;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw new RuntimeException("Error al guardar cliente: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // NUEVO: usado por AuthService.refrescarToken() para reconstruir los datos
    // actuales del cliente a partir del id que viene dentro del refresh token.
    public Optional<Cliente> buscarPorId(Integer idCliente) {
        EntityManager em = emf.createEntityManager();
        try {
            return Optional.ofNullable(em.find(Cliente.class, idCliente));
        } finally {
            em.close();
        }
    }

    public Optional<Cliente> buscarPorCorreo(String correo) {
        if (correo == null || correo.trim().isEmpty()) return Optional.empty();
        EntityManager em = emf.createEntityManager();
        try {
            Cliente cliente = em.createQuery("SELECT c FROM Cliente c WHERE c.correo = :correo", Cliente.class)
                    .setParameter("correo", correo)
                    .getSingleResult();
            return Optional.of(cliente);
        } catch (NoResultException e) {
            return Optional.empty();
        } finally {
            em.close();
        }
    }

    public Optional<Cliente> buscarPorTelefono(String telefono) {
        if (telefono == null || telefono.trim().isEmpty()) return Optional.empty();
        EntityManager em = emf.createEntityManager();
        try {
            Cliente cliente = em.createQuery("SELECT c FROM Cliente c WHERE c.telefono = :telefono", Cliente.class)
                    .setParameter("telefono", telefono)
                    .getSingleResult();
            return Optional.of(cliente);
        } catch (NoResultException e) {
            return Optional.empty();
        } finally {
            em.close();
        }
    }

    // NUEVO: usado para migrar contraseñas viejas en texto plano a hash bcrypt
    // automáticamente la primera vez que el cliente inicia sesión con éxito.
    public void actualizarContrasena(Integer idCliente, String nuevaContrasenaHasheada) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Cliente c = em.find(Cliente.class, idCliente);
            if (c != null) {
                c.setContrasena(nuevaContrasenaHasheada);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
        } finally {
            em.close();
        }
    }
}