package com.logismart.repository;

import com.logismart.domain.Shipment;
import com.logismart.domain.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findAllByOrderByShipDateDescIdDesc();

    List<Shipment> findByStatusOrderByShipDateDescIdDesc(ShipmentStatus status);

    List<Shipment> findByStatusIn(Collection<ShipmentStatus> statuses);

    List<Shipment> findByStatus(ShipmentStatus status);

    Optional<Shipment> findByOrderId(Long orderId);
}
