package com.persPrjUBB.priceMonitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    /**
     * pricemonitor_products_tracked: how many products are in the database right now.
     * The repository is resolved lazily (on scrape), so registering the gauge does not
     * force JPA to start while the meter registry is still being built.
     */
    @Bean
    MeterBinder trackedProductsGauge(ObjectProvider<ProductRepository> products) {
        return registry -> Gauge.builder("pricemonitor.products.tracked", products, p -> p.getObject().count())
                .description("Products currently tracked")
                .register(registry);
    }
}
