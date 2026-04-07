package com.rideflow.rideservice.repository;

import com.rideflow.rideservice.domain.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {
}
