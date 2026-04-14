package com.rideflow.rideservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideflow.rideservice.domain.OutboxEvent;
import com.rideflow.rideservice.domain.Ride;
import com.rideflow.rideservice.domain.RideStatus;
import com.rideflow.rideservice.dto.CancelRideRequest;
import com.rideflow.rideservice.dto.CreateRideRequest;
import com.rideflow.rideservice.dto.RideResponse;
import com.rideflow.rideservice.exception.InvalidRideTransitionException;
import com.rideflow.rideservice.repository.OutboxEventRepository;
import com.rideflow.rideservice.repository.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RideService rideService;

    @Captor
    private ArgumentCaptor<Ride> rideCaptor;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    private UUID riderId;
    private UUID rideId;
    private UUID driverId;
    private CreateRideRequest createRideRequest;

    @BeforeEach
    void setUp() {
        riderId = UUID.randomUUID();
        rideId = UUID.randomUUID();
        driverId = UUID.randomUUID();

        createRideRequest = CreateRideRequest.builder()
                .riderId(riderId)
                .pickupLat(12.9716)
                .pickupLng(77.5946)
                .dropLat(12.2958)
                .dropLng(76.6394)
                .build();
    }

    @Test
    @DisplayName("createRide with valid request saves ride and outbox event")
    void createRide_validRequest_savesRideAndOutboxEvent() throws JsonProcessingException {
        Ride savedRide = buildRide(rideId, riderId, RideStatus.REQUESTED);
        when(rideRepository.save(any(Ride.class))).thenReturn(savedRide);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"rideId\":\"" + rideId + "\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(OutboxEvent.builder().build());

        RideResponse response = rideService.createRide(createRideRequest);

        verify(rideRepository, times(1)).save(any(Ride.class));
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.getId()).isEqualTo(rideId);
        assertThat(response.getRiderId()).isEqualTo(riderId);
    }

    @Test
    @DisplayName("createRide saves ride with REQUESTED status")
    void createRide_savesRideWithStatusRequested() throws JsonProcessingException {
        Ride savedRide = buildRide(rideId, riderId, RideStatus.REQUESTED);
        when(rideRepository.save(rideCaptor.capture())).thenReturn(savedRide);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(OutboxEvent.builder().build());

        rideService.createRide(createRideRequest);

        Ride capturedRide = rideCaptor.getValue();
        assertThat(capturedRide.getStatus()).isEqualTo(RideStatus.REQUESTED);
        assertThat(capturedRide.getRiderId()).isEqualTo(riderId);
        assertThat(capturedRide.getPickupLat()).isEqualTo(12.9716);
        assertThat(capturedRide.getPickupLng()).isEqualTo(77.5946);
        assertThat(capturedRide.getDropLat()).isEqualTo(12.2958);
        assertThat(capturedRide.getDropLng()).isEqualTo(76.6394);
    }

    @Test
    @DisplayName("updateStatus with valid transition updates status")
    void updateStatus_validTransition_updatesStatus() throws JsonProcessingException {
        Ride ride = buildRide(rideId, riderId, RideStatus.DRIVER_ASSIGNED);
        ride.setDriverId(driverId);
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(OutboxEvent.builder().build());

        RideResponse response = rideService.startRide(rideId);

        assertThat(response.getStatus()).isEqualTo(RideStatus.STARTED);
        verify(rideRepository).save(rideCaptor.capture());
        assertThat(rideCaptor.getValue().getStatus()).isEqualTo(RideStatus.STARTED);
    }

    @Test
    @DisplayName("updateStatus with invalid transition throws InvalidRideTransitionException")
    void updateStatus_invalidTransition_throwsInvalidRideTransitionException() {
        Ride ride = buildRide(rideId, riderId, RideStatus.REQUESTED);
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRide(rideId))
                .isInstanceOf(InvalidRideTransitionException.class)
                .hasMessageContaining("Invalid ride status transition from REQUESTED to COMPLETED");
    }

    @Test
    @DisplayName("cancelRide in REQUESTED state cancels successfully")
    void cancelRide_rideInRequestedState_cancelsSuccessfully() throws JsonProcessingException {
        Ride ride = buildRide(rideId, riderId, RideStatus.REQUESTED);
        CancelRideRequest cancelRequest = CancelRideRequest.builder()
                .cancelledBy("RIDER")
                .build();

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(OutboxEvent.builder().build());

        RideResponse response = rideService.cancelRide(rideId, cancelRequest);

        assertThat(response.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.getCancelledBy()).isEqualTo("RIDER");
        verify(rideRepository).save(rideCaptor.capture());
        assertThat(rideCaptor.getValue().getStatus()).isEqualTo(RideStatus.CANCELLED);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("cancelRide in PAID state throws exception")
    void cancelRide_rideInPaidState_throwsException() {
        Ride ride = buildRide(rideId, riderId, RideStatus.PAID);
        CancelRideRequest cancelRequest = CancelRideRequest.builder()
                .cancelledBy("RIDER")
                .build();

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.cancelRide(rideId, cancelRequest))
                .isInstanceOf(InvalidRideTransitionException.class)
                .hasMessageContaining("Invalid ride status transition from PAID to CANCELLED");
    }

    private Ride buildRide(UUID id, UUID riderId, RideStatus status) {
        return Ride.builder()
                .id(id)
                .riderId(riderId)
                .status(status)
                .pickupLat(12.9716)
                .pickupLng(77.5946)
                .dropLat(12.2958)
                .dropLng(76.6394)
                .fareEstimate(BigDecimal.valueOf(250.00))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
