package com.example.ecomonitoring.repository;

import com.example.ecomonitoring.entity.Location;
import com.example.ecomonitoring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByUser(User user);

    Optional<Location> findByIdAndUser(Long id, User user);

    List<Location> findByUserAndIsDefaultTrue(User user);

    void deleteByUser(User user);
}