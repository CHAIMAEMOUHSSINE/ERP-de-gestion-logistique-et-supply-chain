package com.logismart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    // ------------------------------------------------------------------ helpers

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String response = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private ResultActions send(String path, String token, Object body) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ sécurité

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", "admin", "password", "nope"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminCanDeleteProducts() throws Exception {
        String manager = login("manager", "manager123");
        String admin = login("admin", "admin123");

        var product = java.util.Map.of("sku", "TST-DEL-1", "name", "Produit jetable", "category", "Test",
                "unitPrice", 10, "weightKg", 1, "stockQty", 5, "reorderLevel", 1, "leadTimeDays", 3);
        long id = json(send("/api/products", manager, product).andExpect(status().isCreated())).get("id").asLong();

        mvc.perform(delete("/api/products/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ flux métier

    @Test
    void orderFlowUpdatesStockAndShipment() throws Exception {
        String token = login("manager", "manager123");

        JsonNode product = null;
        for (JsonNode p : getJson("/api/products", token)) {
            if (p.get("stockQty").asInt() >= 10) {
                product = p;
                break;
            }
        }
        assertNotNull(product, "le jeu de démonstration doit contenir un produit avec du stock");
        long productId = product.get("id").asLong();
        int stockBefore = product.get("stockQty").asInt();
        long customerId = getJson("/api/customers", token).get(0).get("id").asLong();

        // création -> PENDING
        var request = java.util.Map.of("customerId", customerId,
                "lines", java.util.List.of(java.util.Map.of("productId", productId, "quantity", 3)));
        JsonNode order = json(send("/api/orders", token, request).andExpect(status().isCreated()));
        long orderId = order.get("id").asLong();
        assertEquals("PENDING", order.get("status").asText());
        assertTrue(order.get("reference").asText().startsWith("CMD-"));
        assertEquals(stockBefore, getJson("/api/products/" + productId, token).get("stockQty").asInt());

        // confirmation -> stock décrémenté + expédition créée par l'IA
        order = json(send("/api/orders/" + orderId + "/confirm", token, null).andExpect(status().isOk()));
        assertEquals("CONFIRMED", order.get("status").asText());
        assertFalse(order.get("shipment").isNull());
        assertFalse(order.get("shipment").get("carrier").asText().isBlank());
        assertEquals(stockBefore - 3, getJson("/api/products/" + productId, token).get("stockQty").asInt());
        long shipmentId = order.get("shipment").get("id").asLong();

        // expédition puis livraison à l'heure
        JsonNode shipment = json(send("/api/shipments/" + shipmentId + "/dispatch", token, null)
                .andExpect(status().isOk()));
        assertEquals("IN_TRANSIT", shipment.get("status").asText());
        assertFalse(shipment.get("riskScore").isNull());

        shipment = json(send("/api/shipments/" + shipmentId + "/deliver?delayDays=0", token, null)
                .andExpect(status().isOk()));
        assertEquals("DELIVERED", shipment.get("status").asText());
        assertFalse(shipment.get("delayed").asBoolean());
        assertEquals("DELIVERED", getJson("/api/orders/" + orderId, token).get("status").asText());
    }

    @Test
    void cancellingAConfirmedOrderRestoresStock() throws Exception {
        String token = login("manager", "manager123");
        JsonNode product = null;
        for (JsonNode p : getJson("/api/products", token)) {
            if (p.get("stockQty").asInt() >= 10) {
                product = p;
                break;
            }
        }
        assertNotNull(product);
        long productId = product.get("id").asLong();
        int stockBefore = product.get("stockQty").asInt();
        long customerId = getJson("/api/customers", token).get(0).get("id").asLong();

        var request = java.util.Map.of("customerId", customerId,
                "lines", java.util.List.of(java.util.Map.of("productId", productId, "quantity", 2)));
        long orderId = json(send("/api/orders", token, request)).get("id").asLong();
        send("/api/orders/" + orderId + "/confirm", token, null).andExpect(status().isOk());
        assertEquals(stockBefore - 2, getJson("/api/products/" + productId, token).get("stockQty").asInt());

        JsonNode cancelled = json(send("/api/orders/" + orderId + "/cancel", token, null).andExpect(status().isOk()));
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertEquals(stockBefore, getJson("/api/products/" + productId, token).get("stockQty").asInt());
    }

    @Test
    void confirmingWithInsufficientStockIsRejected() throws Exception {
        String token = login("manager", "manager123");
        long productId = getJson("/api/products", token).get(0).get("id").asLong();
        long customerId = getJson("/api/customers", token).get(0).get("id").asLong();

        var request = java.util.Map.of("customerId", customerId,
                "lines", java.util.List.of(java.util.Map.of("productId", productId, "quantity", 999_999)));
        long orderId = json(send("/api/orders", token, request)).get("id").asLong();

        send("/api/orders/" + orderId + "/confirm", token, null).andExpect(status().isConflict());
    }

    @Test
    void invalidOrderPayloadIsRejected() throws Exception {
        String token = login("manager", "manager123");
        var request = java.util.Map.of("customerId", 1, "lines", java.util.List.of());
        send("/api/orders", token, request).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ IA & dashboard

    @Test
    void aiEndpointsReturnUsefulResults() throws Exception {
        String token = login("manager", "manager123");

        JsonNode model = getJson("/api/ai/model", token);
        assertTrue(model.get("trained").asBoolean(), "le modèle doit être entraîné au démarrage");
        assertTrue(model.get("auc").asDouble() > 0.6);

        JsonNode forecast = getJson("/api/ai/forecast", token);
        assertTrue(forecast.size() > 10);
        assertEquals(12, forecast.get(0).get("history").size());
        assertEquals(4, forecast.get(0).get("forecast").size());
        boolean hasCritical = false;
        for (JsonNode f : forecast) {
            hasCritical |= "CRITICAL".equals(f.get("status").asText());
        }
        assertTrue(hasCritical, "le jeu de démonstration doit contenir au moins un produit critique");

        JsonNode risk = getJson("/api/ai/delay-risk", token);
        assertTrue(risk.size() > 0);
        double previous = Double.MAX_VALUE;
        for (JsonNode s : risk) {
            double score = s.get("riskScore").asDouble();
            assertTrue(score <= previous, "tri par risque décroissant");
            previous = score;
        }

        var prediction = java.util.Map.of("destinationCity", "Dakhla", "weightKg", 1200);
        JsonNode result = json(send("/api/ai/predict-delay", token, prediction).andExpect(status().isOk()));
        assertEquals(4, result.get("carriers").size());
        assertTrue(result.get("distanceKm").asInt() > 1000);

        assertTrue(getJson("/api/ai/insights", token).size() > 0);
    }

    @Test
    void dashboardAggregatesKpis() throws Exception {
        String token = login("admin", "admin123");

        JsonNode dashboard = getJson("/api/dashboard", token);

        assertTrue(dashboard.get("kpis").get("totalProducts").asInt() >= 18);
        assertEquals(30, dashboard.get("revenueByDay").size());
        assertTrue(dashboard.get("kpis").get("onTimeRate").asDouble() > 0);
        assertTrue(dashboard.get("topProducts").size() > 0);
        assertTrue(dashboard.get("carrierPerformance").size() > 0);
    }
}
