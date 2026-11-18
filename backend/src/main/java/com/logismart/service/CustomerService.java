package com.logismart.service;

import com.logismart.domain.Customer;
import com.logismart.dto.CustomerDtos.CustomerRequest;
import com.logismart.dto.CustomerDtos.CustomerResponse;
import com.logismart.repository.CustomerRepository;
import com.logismart.util.Cities;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return customerRepository.findAllByOrderByNameAsc().stream().map(CustomerService::toResponse).toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (!Cities.exists(request.city())) {
            throw new IllegalArgumentException("Ville non desservie : " + request.city());
        }
        Customer customer = customerRepository.save(
                new Customer(request.name().trim(), request.city(), request.email(), request.phone()));
        return toResponse(customer);
    }

    private static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getCity(), c.getEmail(), c.getPhone());
    }
}
