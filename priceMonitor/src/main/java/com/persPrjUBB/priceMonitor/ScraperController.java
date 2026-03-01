package com.persPrjUBB.priceMonitor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController //anunta Spring: "Asta e ușa prin care intră userii de pe net"
@RequestMapping("/api") //toate comenzile cu /api ___
public class ScraperController {
    @Autowired //aduce serviceul aici
    private ScraperService scraperService;

    @Autowired
    private ProductRepository productRepository; //"bibliotecaru"

    @GetMapping("/scrape")
    public Product scrape(@RequestParam String url) {
        Product produsNou = scraperService.scrapeProduct(url);

        if (produsNou != null) {
            // Verificăm dacă URL-ul e deja în DB
            Optional<Product> produsExistent = productRepository.findByUrl(url);

            if (produsExistent.isPresent()) {
                // Dacă există, luăm obiectul vechi și îi punem datele noi
                Product p = produsExistent.get();
                p.setPrice(produsNou.getPrice());
                p.setTitle(produsNou.getTitle());
                productRepository.save(p);
                System.out.println("🔄 Update preț pentru: " + p.getTitle());
                return p;
            } else {
                // Dacă nu există, îl salvăm de la zero
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
        // Scoatem tot ce avem în baza de date
        return productRepository.findAll();
    }
}
