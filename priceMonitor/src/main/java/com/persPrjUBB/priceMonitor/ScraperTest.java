package com.persPrjUBB.priceMonitor; // <--- Verifică să fie numele pachetului tău!

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;

public class ScraperTest {
    public static void main(String[] args) {
        // 1. Alegem o "țintă". Am pus un link de iPhone 15 de pe eMAG.
        // Poți schimba link-ul cu orice produs vrei tu, dar să fie de pe eMAG ca să meargă selectorii.
        String url = "https://www.emag.ro/telefon-mobil-apple-iphone-17-pro-256gb-5g-cosmic-orange-mg8h4zd-a/pd/D099FV3BM/";

        System.out.println("⏳ Mă conectez la site...");

        try {
            // 2. Jsoup se conectează la site și descarcă tot codul HTML (ca și cum ai da View Source)
            Document pagina = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36") // Ne prefacem că suntem un browser real, nu un robot
                    .get();

            // 3. Căutăm titlul produsului
            // Pe eMAG, titlul este într-un tag <h1> care are clasa "page-title"
            Element titluElement = pagina.select("h1.page-title").first();

            // 4. Căutăm prețul
            // Prețul e de obicei într-un paragraf cu clasa "product-new-price"
            Element pretElement = pagina.select(".product-new-price").first();

            // 5. Afișăm rezultatul
            System.out.println("------------------------------------------------");
            if (titluElement != null) {
                System.out.println("📱 Produs: " + titluElement.text());
            } else {
                System.out.println("⚠️ Nu am găsit titlul. Poate s-a schimbat site-ul?");
            }

            if (pretElement != null) {
                // Curățăm textul (eMAG pune uneori "Lei" sau puncte ciudate)
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