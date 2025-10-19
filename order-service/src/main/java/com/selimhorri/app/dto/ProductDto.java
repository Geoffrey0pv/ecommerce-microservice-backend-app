package com.selimhorri.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    
    private Integer productId;
    private String productName;
    private String productDesc;
    private Double productPrice;
    private Integer productStock;
    private String productImage;
    
    // Campos adicionales para compatibilidad con tests
    private String productTitle;
    private String imageUrl;
    private String sku;
    private Double priceUnit;
    private Integer quantity;
    
}
