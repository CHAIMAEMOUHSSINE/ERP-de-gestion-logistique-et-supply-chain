package com.logismart.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    public static final String REASON_ORDER = "ORDER";
    public static final String REASON_RECEPTION = "RECEPTION";
    public static final String REASON_CANCEL = "CANCEL";
    public static final String REASON_ADJUST = "ADJUST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType movementType;

    private int quantity;

    @Column(nullable = false)
    private String reason;

    private String reference;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public StockMovement(Product product, MovementType type, int quantity, String reason,
                         String reference, LocalDateTime createdAt) {
        this.product = product;
        this.movementType = type;
        this.quantity = quantity;
        this.reason = reason;
        this.reference = reference;
        this.createdAt = createdAt;
    }
}
