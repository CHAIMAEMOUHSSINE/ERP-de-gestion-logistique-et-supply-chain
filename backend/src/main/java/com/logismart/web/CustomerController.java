package com.logismart.web;

import com.logismart.dto.CustomerDtos.CustomerRequest;
import com.logismart.dto.CustomerDtos.CustomerResponse;
import com.logismart.service.CustomerService;
import com.logismart.util.Carriers;
import com.logismart.util.Cities;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/customers")
    public List<CustomerResponse> list() {
        return customerService.list();
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    /** Listes de référence pour les formulaires (villes desservies, transporteurs). */
    @GetMapping("/meta")
    public Map<String, List<String>> meta() {
        return Map.of("cities", Cities.names(), "carriers", Carriers.ALL);
    }
}
