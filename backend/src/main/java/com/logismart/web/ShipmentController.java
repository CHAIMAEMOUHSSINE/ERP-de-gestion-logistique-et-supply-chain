package com.logismart.web;

import com.logismart.domain.ShipmentStatus;
import com.logismart.dto.ShipmentDtos.ShipmentResponse;
import com.logismart.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping
    public List<ShipmentResponse> list(@RequestParam(required = false) ShipmentStatus status) {
        return shipmentService.list(status);
    }

    @GetMapping("/{id}")
    public ShipmentResponse get(@PathVariable Long id) {
        return shipmentService.get(id);
    }

    @PostMapping("/{id}/dispatch")
    public ShipmentResponse dispatch(@PathVariable Long id) {
        return shipmentService.dispatch(id);
    }

    /**
     * delayDays (facultatif) : simule une livraison à la date prévue + N jours (0 = à l'heure).
     * Sans ce paramètre, la livraison est enregistrée à la date du jour.
     */
    @PostMapping("/{id}/deliver")
    public ShipmentResponse deliver(@PathVariable Long id, @RequestParam(required = false) Integer delayDays) {
        return shipmentService.deliver(id, delayDays);
    }
}
