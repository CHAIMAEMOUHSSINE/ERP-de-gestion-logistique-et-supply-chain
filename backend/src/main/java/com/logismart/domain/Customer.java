package com.logismart.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private String email;

    private String phone;

    public Customer(String name, String city, String email, String phone) {
        this.name = name;
        this.city = city;
        this.email = email;
        this.phone = phone;
    }
}
