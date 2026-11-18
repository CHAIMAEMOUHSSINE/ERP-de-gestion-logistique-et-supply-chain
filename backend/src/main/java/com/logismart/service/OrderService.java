package com.logismart.service;

import com.logismart.domain.*;
import com.logismart.dto.OrderDtos.*;
import com.logismart.repository.*;
import com.logismart.util.BusinessException;
import com.logismart.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cycle de vie d'une commande : PENDING -> CONFIRMED (stock décrémenté + expédition créée)
 * -> SHIPPED -> DELIVERED, ou CANCELLED.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;

    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        List<SalesOrder> orders = status == null
                ? orderRepository.findAllByOrderByCreatedAtDesc()
                : orderRepository.findByStatusOrderByCreatedAtDesc(status);
        return orders.stream().map(OrderService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Client introuvable : " + request.customerId()));

        // Regroupe les lignes d'un même produit
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineRequest line : request.lines()) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }

        SalesOrder order = new SalesOrder();
        order.setReference("TMP-" + UUID.randomUUID());
        order.setCustomer(customer);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + entry.getKey()));
            OrderLine line = new OrderLine(product, entry.getValue());
            order.addLine(line);
            total = total.add(line.lineTotal());
        }
        order.setTotalAmount(total);
        order = orderRepository.save(order);
        order.setReference(String.format("CMD-%05d", order.getId()));
        return toResponse(order);
    }

    /** Confirme la commande : vérifie et décrémente le stock, puis crée l'expédition. */
    @Transactional
    public OrderResponse confirm(Long id, String carrier) {
        SalesOrder order = find(id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Seule une commande en attente peut être confirmée.");
        }
        for (OrderLine line : order.getLines()) {
            Product product = line.getProduct();
            if (product.getStockQty() < line.getQuantity()) {
                throw new BusinessException(String.format(
                        "Stock insuffisant pour %s : demandé %d, disponible %d.",
                        product.getName(), line.getQuantity(), product.getStockQty()));
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (OrderLine line : order.getLines()) {
            Product product = line.getProduct();
            product.setStockQty(product.getStockQty() - line.getQuantity());
            movementRepository.save(new StockMovement(product, MovementType.OUT, line.getQuantity(),
                    StockMovement.REASON_ORDER, order.getReference(), now));
        }
        order.setStatus(OrderStatus.CONFIRMED);
        shipmentService.createForOrder(order, carrier);
        return toResponse(order);
    }

    /** Annule la commande. Si elle était confirmée (et pas encore expédiée), le stock est restitué. */
    @Transactional
    public OrderResponse cancel(Long id) {
        SalesOrder order = find(id);
        switch (order.getStatus()) {
            case PENDING -> order.setStatus(OrderStatus.CANCELLED);
            case CONFIRMED -> {
                LocalDateTime now = LocalDateTime.now();
                for (OrderLine line : order.getLines()) {
                    Product product = line.getProduct();
                    product.setStockQty(product.getStockQty() + line.getQuantity());
                    movementRepository.save(new StockMovement(product, MovementType.IN, line.getQuantity(),
                            StockMovement.REASON_CANCEL, order.getReference(), now));
                }
                if (order.getShipment() != null) {
                    shipmentRepository.delete(order.getShipment());
                    order.setShipment(null);
                }
                order.setStatus(OrderStatus.CANCELLED);
            }
            default -> throw new BusinessException(
                    "Impossible d'annuler une commande au statut " + order.getStatus() + ".");
        }
        return toResponse(order);
    }

    private SalesOrder find(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable : " + id));
    }

    static OrderResponse toResponse(SalesOrder o) {
        List<OrderLineResponse> lines = o.getLines().stream()
                .map(l -> new OrderLineResponse(l.getProduct().getId(), l.getProduct().getSku(),
                        l.getProduct().getName(), l.getQuantity(), l.getUnitPrice(), l.lineTotal()))
                .toList();
        Shipment s = o.getShipment();
        ShipmentSummary summary = s == null ? null
                : new ShipmentSummary(s.getId(), s.getReference(), s.getStatus().name(), s.getCarrier(),
                s.getPlannedDelivery());
        return new OrderResponse(o.getId(), o.getReference(), o.getCustomer().getId(), o.getCustomer().getName(),
                o.getCustomer().getCity(), o.getStatus().name(), o.getCreatedAt(), o.getTotalAmount(), lines, summary);
    }
}
