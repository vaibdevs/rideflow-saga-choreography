package com.rideflow.paymentservice.repository;

import com.rideflow.paymentservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByRideId(UUID rideId);

    Optional<Payment> findByRideId(UUID rideId);
}
