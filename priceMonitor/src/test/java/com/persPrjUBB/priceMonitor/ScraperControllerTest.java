package com.persPrjUBB.priceMonitor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API contract for prices did not change when they moved from double to BigDecimal:
 * they are still JSON numbers, never strings.
 */
@WebMvcTest(ScraperController.class)
class ScraperControllerTest {

    private static final String URL = "https://www.emag.ro/laptop-test-book-15/pd/TESTSKU1/";
    private static final Instant CHECKED_AT = Instant.parse("2026-11-16T21:44:27Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PriceService prices;

    @Test
    void productPriceIsAJsonNumber() throws Exception {
        when(prices.all()).thenReturn(List.of(product(1L, "3499.90"), product(2L, "1299.00")));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPrice").isNumber())
                .andExpect(jsonPath("$[0].currentPrice").value(3499.90))
                .andExpect(jsonPath("$[1].currentPrice").value(1299.00))
                // unquoted in the raw body, and not in scientific notation
                .andExpect(content().string(containsString("\"currentPrice\":3499.90,")))
                .andExpect(content().string(containsString("\"currentPrice\":1299.00,")));
    }

    @Test
    void historyPriceIsAJsonNumber() throws Exception {
        Product product = product(1L, "89.99");
        when(prices.history(1L)).thenReturn(List.of(
                new PricePoint(product, new BigDecimal("100.00"), CHECKED_AT),
                new PricePoint(product, new BigDecimal("89.99"), CHECKED_AT)));

        mvc.perform(get("/api/products/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").isNumber())
                .andExpect(jsonPath("$[0].price").value(100.00))
                .andExpect(jsonPath("$[1].price").value(89.99))
                .andExpect(content().string(containsString("\"price\":89.99,")));
    }

    @Test
    void trackReturnsTheNewProductWithANumericPrice() throws Exception {
        when(prices.track(URL)).thenReturn(product(7L, "6399.99"));

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.currentPrice").value(6399.99));
    }

    @Test
    void nonEmagUrlIsRejected() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/x\"}"))
                .andExpect(status().isBadRequest());
    }

    private static Product product(Long id, String price) {
        Product product = new Product(URL);
        product.setId(id);
        product.setTitle("Laptop Test Book 15");
        product.setCurrentPrice(new BigDecimal(price));
        product.setLastChecked(CHECKED_AT);
        return product;
    }
}
