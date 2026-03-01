package com.persPrjUBB.priceMonitor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class PriceScheduler {

    @Autowired
    private ScraperService scraperService;

    @Autowired
    private ProductRepository productRepository;

    @Scheduled(fixedRate = 3600000)
    public void verificaPretulAutomat() {
        List<Product> produseDeMonitorizat = productRepository.findAll();

        System.out.println("🚀 [AUTO] Scanare pornită pentru " + produseDeMonitorizat.size() + " produse...");

        for (Product p : produseDeMonitorizat) {
            try {
                Thread.sleep(5000);

                Product updatedInfo = scraperService.scrapeProduct(p.getUrl());

                if (updatedInfo != null) {
                    p.setPrice(updatedInfo.getPrice());
                    p.setTitle(updatedInfo.getTitle());
                    productRepository.save(p);
                    System.out.println("✅ Update: " + p.getTitle() + " -> " + p.getPrice());
                }
            } catch (Exception e) {
                System.err.println("❌ Eroare la produsul " + p.getUrl() + ": " + e.getMessage());
            }
        }
    }
}