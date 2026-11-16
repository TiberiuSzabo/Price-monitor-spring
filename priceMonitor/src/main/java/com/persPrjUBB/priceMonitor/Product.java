package com.persPrjUBB.priceMonitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;

    // NUMERIC(12,2): exact decimal, never a binary floating-point value
    @Column(precision = 12, scale = 2)
    private BigDecimal currentPrice;

    private Instant lastChecked;

    @Column(unique = true, nullable = false, length = 2048)
    private String url;

    public Product(String url) {
        this.url = url;
    }
}
