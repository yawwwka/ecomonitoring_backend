package com.example.ecomonitoring.repository;

import com.example.ecomonitoring.entity.UserCitySubscription;
import com.example.ecomonitoring.entity.User;
import com.example.ecomonitoring.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserCitySubscriptionRepository extends JpaRepository<UserCitySubscription, Long> {

    List<UserCitySubscription> findByUser(User user);

    Optional<UserCitySubscription> findByUserAndCity(User user, City city);

    void deleteByUserAndCity(User user, City city);

    List<UserCitySubscription> findByCity(City city);
}