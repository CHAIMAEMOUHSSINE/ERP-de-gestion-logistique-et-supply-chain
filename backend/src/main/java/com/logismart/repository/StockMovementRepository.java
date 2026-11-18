package com.logismart.repository;

import com.logismart.domain.MovementType;
import com.logismart.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findTop50ByProductIdOrderByCreatedAtDescIdDesc(Long productId);

    void deleteByProductId(Long productId);

    /** Sorties de stock liées à des commandes depuis une date : base de la prévision de demande. */
    @Query("""
            select m from StockMovement m
            where m.movementType = :type and m.reason = :reason and m.createdAt >= :since
            """)
    List<StockMovement> findDemand(@Param("type") MovementType type,
                                   @Param("reason") String reason,
                                   @Param("since") LocalDateTime since);
}
