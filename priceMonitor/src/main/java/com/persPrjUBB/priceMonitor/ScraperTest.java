package com.persPrjUBB.priceMonitor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;

public class ScraperTest {
    public static void main(String[] args) {

        String url = "https://www.emag.ro/telefon-mobil-apple-iphone-17-pro-256gb-5g-cosmic-orange-mg8h4zd-a/pd/D099FV3BM/";

        System.out.println("⏳ Mă conectez la site...");

        try {
            Document pagina = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();


            Element titluElement = pagina.select("h1.page-title").first();


            Element pretElement = pagina.select(".product-new-price").first();

            System.out.println("------------------------------------------------");
            if (titluElement != null) {
                System.out.println("📱 Produs: " + titluElement.text());
            } else {
                System.out.println("⚠️ Nu am găsit titlul. Poate s-a schimbat site-ul?");
            }

            if (pretElement != null) {
                System.out.println("💰 Preț: " + pretElement.text());
            } else {
                System.out.println("⚠️ Nu am găsit prețul.");
            }
            System.out.println("------------------------------------------------");

        } catch (IOException e) {
            System.out.println("❌ Eroare la conectare: " + e.getMessage());
        }
    }
}