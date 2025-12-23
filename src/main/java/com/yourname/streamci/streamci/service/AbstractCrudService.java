package com.yourname.streamci.streamci.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * base service class for common crud operations
 * eliminates duplicate code across service classes
 */
public abstract class AbstractCrudService<T, ID> {

    protected final JpaRepository<T, ID> repository;

    protected AbstractCrudService(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    /**
     * get all entities
     */
    public List<T> getAll() {
        return repository.findAll();
    }

    /**
     * get entity by id
     */
    public Optional<T> getById(ID id) {
        return repository.findById(id);
    }

    /**
     * save new entity or update existing
     */
    public T save(T entity) {
        return repository.save(entity);
    }

    /**
     * delete entity by id
     * @return true if deleted, false if not found
     */
    public boolean delete(ID id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * update entity by id with custom update logic
     * @param id entity id
     * @param updateFunction function to apply updates to existing entity
     * @return updated entity or empty if not found
     */
    public Optional<T> update(ID id, Consumer<T> updateFunction) {
        return repository.findById(id)
                .map(existing -> {
                    updateFunction.accept(existing);
                    return repository.save(existing);
                });
    }
}
