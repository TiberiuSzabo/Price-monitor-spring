package com.persPrjUBB.priceMonitor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScraperService {
    public Product scrapeProduct(String url){
        try{
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 ")
                    .timeout(10000)
                    .get();
            Element titleEl = doc.select("h1.page-title").first();
            Element priceEl = doc.select(".product-new-price").first();

            //creez obiectul folosind constr. gol si settere (de la lombok)
            Product product = new Product();
            product.setUrl(url);

            if(titleEl != null) {
                product.setTitle(titleEl.text());
            }

            if (priceEl != null) {
                String rawPrice = priceEl.text();
                String cleanPrice = rawPrice.replaceAll("[^0-9,]", "")
                        .replace(",", ".");

                product.setPrice(Double.parseDouble(cleanPrice));
            }

            return product;
        } catch (IOException e) {
            System.out.println("⚠️ Scraper Error pe URL-ul: " + url);
            return null;
        }
    }

    public List<String> discoverProducts(String searchUrl) {
        List<String> foundLinks = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0")
                    .get();

            // Selectorul pentru link-urile produselor din pagina de search eMAG
            // De obicei, titlurile sunt în tag-uri <a> cu clasa "card-v2-title"
            Elements links = doc.select("a.card-v2-title");

            for (Element link : links) {
                String href = link.attr("href");
                if (!href.isEmpty()) {
                    foundLinks.add(href);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Eroare la discovery: " + e.getMessage());
        }
        return foundLinks;
    }
}
