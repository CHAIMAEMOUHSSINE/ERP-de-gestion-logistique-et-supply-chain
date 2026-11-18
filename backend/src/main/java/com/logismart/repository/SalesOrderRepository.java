package com.logismart.repository;

import com.logismart.domain.OrderStatus;
import com.logismart.domain.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findAllByOrderByCreatedAtDesc();

    List<SalesOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<SalesOrder> findByCreatedAtAfter(LocalDateTime since);
}
