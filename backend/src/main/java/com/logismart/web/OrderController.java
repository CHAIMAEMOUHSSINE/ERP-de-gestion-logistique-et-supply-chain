package com.logismart.web;

import com.logismart.domain.OrderStatus;
import com.logismart.dto.OrderDtos.OrderRequest;
import com.logismart.dto.OrderDtos.OrderResponse;
import com.logismart.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return orderService.list(status);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody OrderRequest request) {
        return orderService.create(request);
    }

    /** Confirme la commande. Le paramètre carrier est facultatif : sinon l'IA choisit le transporteur. */
    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable Long id, @RequestParam(required = false) String carrier) {
        return orderService.confirm(id, carrier);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(id);
    }
}
