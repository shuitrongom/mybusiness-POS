package com.mybusinesssilva.catalog.adapters.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de categorías (familias) de productos del negocio: crear, renombrar y eliminar.
 *
 * <p>Vive en su PROPIO controlador y ruta base ({@code /api/v1/catalog/categories}), separado del
 * catálogo de productos, para evitar conflictos de mapeo de métodos HTTP en una misma ruta.
 *
 * <p>Autorización: requiere el módulo {@code inventory} habilitado y permiso del rol (igual que la
 * administración del catálogo de productos). La lectura (GET) es más permisiva (también para
 * vender), por lo que acepta quien pueda leer el catálogo.
 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
@PreAuthorize("@moduleAccess.canUse('inventory')")
public class CategoryController {

    private final JdbcClient jdbc;

    public CategoryController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lista las categorías del negocio, ordenadas por nombre. */
    @GetMapping
    @PreAuthorize("@moduleAccess.canReadCatalog()")
    public List<Map<String, Object>> list() {
        return jdbc.sql("SELECT id, name FROM category ORDER BY name")
                .query((rs, n) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("name")))
                .list();
    }

    /** Crea una categoría nueva. El nombre es único por negocio. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CategoryRequest req) {
        String name = req.name().trim();
        Long id = jdbc.sql("INSERT INTO category (name) VALUES (:name) RETURNING id")
                .param("name", name)
                .query(Long.class)
                .single();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "name", name));
    }

    /** Renombra una categoría existente. */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long id, @Valid @RequestBody CategoryRequest req) {
        String name = req.name().trim();
        int updated = jdbc.sql("UPDATE category SET name = :name WHERE id = :id")
                .param("name", name).param("id", id)
                .update();
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("id", id, "name", name));
    }

    /**
     * Elimina una categoría. Los productos que la tuvieran asignada quedan "Sin categoría"
     * (category_id = NULL), para no romper el catálogo ni perder productos.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        jdbc.sql("UPDATE product SET category_id = NULL WHERE category_id = :id")
                .param("id", id).update();
        jdbc.sql("DELETE FROM category WHERE id = :id").param("id", id).update();
        return ResponseEntity.noContent().build();
    }

    /** Alta/edición de categoría (solo el nombre; único por negocio). */
    public record CategoryRequest(@NotBlank String name) {
    }
}
