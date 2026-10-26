package com.persPrjUBB.priceMonitor;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("No tracked product with id " + id);
    }
}
