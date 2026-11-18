package com.logismart.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    @OneToOne(optional = false)
    private SalesOrder order;

    @Column(nullable = false)
    private String carrier;

    @Column(nullable = false)
    private String originCity;

    @Column(nullable = false)
    private String destinationCity;

    private int distanceKm;

    private double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.PLANNED;

    /** Date de départ prévue de l'entrepôt. */
    private LocalDate shipDate;

    /** Date de livraison promise au client. */
    private LocalDate plannedDelivery;

    /** Date de livraison réelle (renseignée à la livraison). */
    private LocalDate deliveredAt;

    /** Vrai si la livraison a eu lieu après la date prévue. Null tant que non livrée. */
    private Boolean delayed;
}
