package com.persPrjUBB.priceMonitor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ScraperController {
    @Autowired
    private ScraperService scraperService;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping("/scrape")
    public Product scrape(@RequestParam String url) {
        Product produsNou = scraperService.scrapeProduct(url);

        if (produsNou != null) {
            Optional<Product> produsExistent = productRepository.findByUrl(url);

            if (produsExistent.isPresent()) {
                Product p = produsExistent.get();
                p.setPrice(produsNou.getPrice());
                p.setTitle(produsNou.getTitle());
                productRepository.save(p);
                System.out.println("🔄 Update preț pentru: " + p.getTitle());
                return p;
            } else {
                productRepository.save(produsNou);
                System.out.println("✅ Produs nou salvat!");
                return produsNou;
            }
        }
        return null;
    }

    @GetMapping("/bulk-add")
    public String bulkAdd(@RequestParam String searchUrl) {
        List<String> links = scraperService.discoverProducts(searchUrl);
        int count = 0;

        for (String link : links) {
            Product p = scraperService.scrapeProduct(link);
            if (p != null) {
                productRepository.save(p);
                count++;
            }
        }
        return "✅ Am găsit și adăugat " + count + " produse noi!";
    }

    @GetMapping("/history")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
}
