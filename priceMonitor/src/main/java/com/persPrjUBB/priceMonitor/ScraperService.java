package com.persPrjUBB.priceMonitor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScraperService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/130.0 Safari/537.36";

    // fallback: "price": "6199" from the JSON-LD block
    private static final Pattern JSON_LD_PRICE = Pattern.compile("\"price\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"?");

    private final int timeoutMs;

    public ScraperService(@Value("${pricemonitor.http-timeout-ms:10000}") int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public ScrapedProduct scrapeProduct(String url) {
        String cleanUrl = normalizeUrl(url);
        return parseProductPage(fetch(cleanUrl), cleanUrl);
    }

    public List<String> discoverProducts(String searchUrl) {
        return parseListingPage(fetch(searchUrl));
    }

    static ScrapedProduct parseProductPage(Document doc, String url) {
        Element titleEl = doc.selectFirst("h1.page-title");
        if (titleEl == null || titleEl.text().isBlank()) {
            throw new ScrapeException("No product title on " + url + " - the page layout may have changed");
        }

        Double price = null;
        Element priceEl = doc.selectFirst("[data-test=main-price]");
        if (priceEl == null) {
            priceEl = doc.selectFirst(".product-new-price");
        }
        if (priceEl != null) {
            price = parseRonPrice(priceEl.text());
        }
        if (price == null) {
            price = priceFromJsonLd(doc);
        }
        if (price == null) {
            throw new ScrapeException("No price on " + url + " - the page layout may have changed");
        }
        return new ScrapedProduct(url, titleEl.text().trim(), price);
    }

    static List<String> parseListingPage(Document doc) {
        Set<String> links = new LinkedHashSet<>();
        for (Element a : doc.select("a.card-v2-title")) {
            String href = a.absUrl("href");
            if (href.isEmpty()) {
                href = a.attr("href");
            }
            if (!href.isEmpty()) {
                links.add(normalizeUrl(href));
            }
        }
        return List.copyOf(links);
    }

    // "6.199,00 Lei" -> 6199.00
    static Double parseRonPrice(String raw) {
        String digits = raw.replaceAll("[^0-9.,]", "").replace(".", "").replace(",", ".");
        if (digits.isEmpty() || digits.equals(".")) {
            return null;
        }
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // listing links have long tracking params, without this the same product gets saved many times
    static String normalizeUrl(String url) {
        try {
            URI u = new URI(url.trim());
            if (u.getScheme() == null || u.getHost() == null) {
                throw new IllegalArgumentException("Not an absolute URL: " + url);
            }
            return new URI(u.getScheme(), u.getAuthority(), u.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    private static Double priceFromJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json.contains("\"Product\"") && json.contains("\"offers\"")) {
                Matcher m = JSON_LD_PRICE.matcher(json.substring(json.indexOf("\"offers\"")));
                if (m.find()) {
                    return Double.parseDouble(m.group(1));
                }
            }
        }
        return null;
    }

    private Document fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ro-RO,ro;q=0.9,en;q=0.8")
                    .timeout(timeoutMs)
                    .get();
        } catch (IOException e) {
            throw new ScrapeException("Could not fetch " + url + ": " + e.getMessage(), e);
        }
    }
}
