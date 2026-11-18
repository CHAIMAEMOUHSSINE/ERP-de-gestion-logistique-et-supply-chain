package com.logismart.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Poids unitaire en kg, utilisé pour calculer le poids d'une expédition. */
    private double weightKg;

    private int stockQty;

    /** Seuil en dessous duquel le produit est considéré en stock bas. */
    private int reorderLevel;

    /** Délai de réapprovisionnement fournisseur (en jours). */
    private int leadTimeDays;

    private LocalDateTime createdAt = LocalDateTime.now();
}
