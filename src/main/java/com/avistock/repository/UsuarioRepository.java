package com.avistock.repository;

import com.avistock.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import java.util.Optional;

public class UsuarioRepository {

    private final EntityManagerFactory emf;

    public UsuarioRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public Optional<Usuario> buscarPorCorreo(String correo) {
        if (correo == null || correo.trim().isEmpty()) return Optional.empty();
        EntityManager em = emf.createEntityManager();
        try {
            Usuario usuario = em.createQuery("SELECT u FROM Usuario u WHERE u.correo = :correo", Usuario.class)
                    .setParameter("correo", correo)
                    .getSingleResult();
            return Optional.of(usuario);
        } catch (NoResultException e) {
            return Optional.empty();
        } finally {
            em.close();
        }
    }

    // NUEVO: usado para migrar contraseñas viejas en texto plano a hash bcrypt
    // automáticamente la primera vez que el usuario inicia sesión con éxito.
    public void actualizarContrasena(Integer idUsuario, String nuevaContrasenaHasheada) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            Usuario u = em.find(Usuario.class, idUsuario);
            if (u != null) {
                u.setContrasena(nuevaContrasenaHasheada);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
        } finally {
            em.close();
        }
    }
}