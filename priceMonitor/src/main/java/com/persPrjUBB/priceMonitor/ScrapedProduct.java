package com.persPrjUBB.priceMonitor;

import java.math.BigDecimal;

public record ScrapedProduct(String url, String title, BigDecimal price) {
}
