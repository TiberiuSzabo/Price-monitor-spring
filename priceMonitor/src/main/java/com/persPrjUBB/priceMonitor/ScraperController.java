package com.persPrjUBB.priceMonitor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ScraperController {

    public record TrackRequest(String url) {}

    public record ProductDto(Long id, String title, Double currentPrice, Instant lastChecked, String url) {
        static ProductDto of(Product p) {
            return new ProductDto(p.getId(), p.getTitle(), p.getCurrentPrice(), p.getLastChecked(), p.getUrl());
        }
    }

    public record PricePointDto(double price, Instant checkedAt) {
        static PricePointDto of(PricePoint p) {
            return new PricePointDto(p.getPrice(), p.getCheckedAt());
        }
    }

    public record DiscoverResult(int tracked) {}

    private final PriceService prices;

    public ScraperController(PriceService prices) {
        this.prices = prices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto track(@RequestBody TrackRequest request) {
        return ProductDto.of(prices.track(requireEmagUrl(request.url())));
    }

    @PostMapping("/discover")
    public DiscoverResult discover(@RequestBody TrackRequest request) {
        return new DiscoverResult(prices.trackAllFrom(requireEmagUrl(request.url())));
    }

    @GetMapping
    public List<ProductDto> all() {
        return prices.all().stream().map(ProductDto::of).toList();
    }

    @GetMapping("/{id}/history")
    public List<PricePointDto> history(@PathVariable Long id) {
        return prices.history(id).stream().map(PricePointDto::of).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untrack(@PathVariable Long id) {
        prices.untrack(id);
    }

    static String requireEmagUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        String host = URI.create(url.trim()).getHost();
        if (host == null || !(host.equals("emag.ro") || host.endsWith(".emag.ro"))) {
            throw new IllegalArgumentException("Only emag.ro URLs are supported");
        }
        return url.trim();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail notFound(ProductNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ScrapeException.class)
    ProblemDetail scrapeFailed(ScrapeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
