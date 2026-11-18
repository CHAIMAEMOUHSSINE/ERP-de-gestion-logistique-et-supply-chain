package com.logismart.repository;

import com.logismart.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    @Query("""
            select p from Product p
            where lower(p.name) like lower(concat('%', :q, '%'))
               or lower(p.sku) like lower(concat('%', :q, '%'))
               or lower(p.category) like lower(concat('%', :q, '%'))
            order by p.name
            """)
    List<Product> search(@Param("q") String q);

    List<Product> findAllByOrderByNameAsc();
}
