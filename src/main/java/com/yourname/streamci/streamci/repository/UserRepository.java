package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByClerkUserId(String clerkUserId);
    boolean existsByClerkUserId(String clerkUserId);
}