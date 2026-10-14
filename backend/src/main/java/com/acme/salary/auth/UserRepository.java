package com.acme.salary.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for users. Spring writes the implementation from the method names. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);
}
