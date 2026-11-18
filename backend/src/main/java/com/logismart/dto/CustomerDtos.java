package com.logismart.dto;

import jakarta.validation.constraints.NotBlank;

public final class CustomerDtos {

    private CustomerDtos() {}

    public record CustomerRequest(@NotBlank String name, @NotBlank String city, String email, String phone) {}

    public record CustomerResponse(Long id, String name, String city, String email, String phone) {}
}
