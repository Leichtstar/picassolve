package dev.starq.picassolve.repository;

import dev.starq.picassolve.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByName(String name);
    boolean existsByName(String name);
    List<User> findByNameIn(Collection<String> names);
}
