package com.logismart.service;

import com.logismart.domain.MovementType;
import com.logismart.domain.Product;
import com.logismart.domain.StockMovement;
import com.logismart.dto.ProductDtos.*;
import com.logismart.repository.ProductRepository;
import com.logismart.repository.StockMovementRepository;
import com.logismart.util.BusinessException;
import com.logismart.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String search) {
        List<Product> products = (search == null || search.isBlank())
                ? productRepository.findAllByOrderByNameAsc()
                : productRepository.search(search.trim());
        return products.stream().map(ProductService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessException("Le SKU " + request.sku() + " existe déjà.");
        }
        Product product = new Product();
        apply(product, request);
        product = productRepository.save(product);
        if (product.getStockQty() > 0) {
            movementRepository.save(new StockMovement(product, MovementType.IN, product.getStockQty(),
                    StockMovement.REASON_RECEPTION, "Stock initial", LocalDateTime.now()));
        }
        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new BusinessException("Le SKU " + request.sku() + " existe déjà.");
        }
        int previousStock = product.getStockQty();
        apply(product, request);
        int diff = product.getStockQty() - previousStock;
        if (diff != 0) {
            movementRepository.save(new StockMovement(product,
                    diff > 0 ? MovementType.IN : MovementType.OUT, Math.abs(diff),
                    StockMovement.REASON_ADJUST, "Ajustement manuel", LocalDateTime.now()));
        }
        return toResponse(product);
    }

    /**
     * Supprime un produit et son historique de mouvements. Refusé (409) si le produit figure
     * dans des commandes : la contrainte de clé étrangère annule alors toute l'opération.
     */
    @Transactional
    public void delete(Long id) {
        Product product = find(id);
        movementRepository.deleteByProductId(id);
        productRepository.delete(product);
        productRepository.flush();
    }

    /** Réception de marchandise fournisseur : augmente le stock. */
    @Transactional
    public ProductResponse receive(Long id, ReceiveRequest request) {
        Product product = find(id);
        product.setStockQty(product.getStockQty() + request.quantity());
        String reference = (request.reference() == null || request.reference().isBlank())
                ? "Réception fournisseur" : request.reference().trim();
        movementRepository.save(new StockMovement(product, MovementType.IN, request.quantity(),
                StockMovement.REASON_RECEPTION, reference, LocalDateTime.now()));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movements(Long id) {
        find(id);
        return movementRepository.findTop50ByProductIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(m -> new MovementResponse(m.getId(), m.getMovementType().name(), m.getQuantity(),
                        m.getReason(), m.getReference(), m.getCreatedAt()))
                .toList();
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + id));
    }

    private static void apply(Product product, ProductRequest request) {
        product.setSku(request.sku().trim());
        product.setName(request.name().trim());
        product.setCategory(request.category());
        product.setUnitPrice(request.unitPrice());
        product.setWeightKg(request.weightKg());
        product.setStockQty(request.stockQty());
        product.setReorderLevel(request.reorderLevel());
        product.setLeadTimeDays(request.leadTimeDays());
    }

    static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getUnitPrice(),
                p.getWeightKg(), p.getStockQty(), p.getReorderLevel(), p.getLeadTimeDays(),
                p.getStockQty() <= p.getReorderLevel());
    }
}
