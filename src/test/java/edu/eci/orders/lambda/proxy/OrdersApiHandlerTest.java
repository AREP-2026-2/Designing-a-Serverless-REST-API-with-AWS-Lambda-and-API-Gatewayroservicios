package edu.eci.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import edu.eci.orders.application.OrderRepository;
import edu.eci.orders.application.OrderService;
import edu.eci.orders.infrastructure.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdersApiHandlerTest {

    private OrdersApiHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrdersApiHandler(new OrderService(InMemoryOrderRepository.withSampleData()));
    }

    @Test
    void getExistingOrderReturns200() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/orders/{id}").withPathParameters(Map.of("id", "ORD-001")), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("{\"id\":\"ORD-001\",\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}",
                response.getBody());
    }

    @Test
    void getMissingOrderReturns404WithErrorBody() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/orders/{id}").withPathParameters(Map.of("id", "ORD-999")), null);

        assertEquals(404, response.getStatusCode());
        assertEquals("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}",
                response.getBody());
    }

    @Test
    void findByCustomerReturns200WithArray() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/orders").withQueryStringParameters(Map.of("customerId", "CUST-42")), null);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().startsWith("[{\"id\":\"ORD-001\""));
    }

    @Test
    void findCustomerWithoutOrdersReturnsEmptyArray() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/orders").withQueryStringParameters(Map.of("customerId", "CUST-100")), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("[]", response.getBody());
    }

    @Test
    void findWithoutCustomerIdReturns400() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(request("GET", "/orders"), null);

        assertEquals(400, response.getStatusCode());
        assertEquals("{\"error\":\"INVALID_REQUEST\",\"message\":\"customerId is required\"}", response.getBody());
    }

    @Test
    void createReturns201WithLocationHeader() {
        APIGatewayProxyRequestEvent request = request("POST", "/orders")
                .withBody("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}")
                .withRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext().withPath("/dev/orders"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

        assertEquals(201, response.getStatusCode());
        assertEquals("/dev/orders/ORD-004", response.getHeaders().get("Location"));
        assertEquals("{\"id\":\"ORD-004\",\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}",
                response.getBody());
    }

    @Test
    void createWithInvalidQuantityReturns400() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(request("POST", "/orders")
                .withBody("{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":0}"), null);

        assertEquals(400, response.getStatusCode());
        assertEquals("{\"error\":\"INVALID_REQUEST\",\"message\":\"quantity must be greater than 0\"}",
                response.getBody());
    }

    @Test
    void createWithMalformedJsonReturns400() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("POST", "/orders").withBody("{\"customerId\":"), null);

        assertEquals(400, response.getStatusCode());
        assertEquals("{\"error\":\"INVALID_REQUEST\",\"message\":\"Request body is not valid JSON\"}",
                response.getBody());
    }

    @Test
    void createWithWrongTypeReturns400() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(request("POST", "/orders")
                .withBody("{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":\"abc\"}"), null);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void createWithoutBodyReturns400() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(request("POST", "/orders"), null);

        assertEquals(400, response.getStatusCode());
        assertEquals("{\"error\":\"INVALID_REQUEST\",\"message\":\"Request body is required\"}", response.getBody());
    }

    @Test
    void deleteExistingOrderReturns204WithoutBody() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(delete("ORD-001"), null);

        assertEquals(204, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void deleteMissingOrderReturns404() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(delete("ORD-999"), null);

        assertEquals(404, response.getStatusCode());
        assertEquals("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}",
                response.getBody());
    }

    @Test
    void getAfterDeleteReturns404() {
        handler.handleRequest(delete("ORD-001"), null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("GET", "/orders/{id}").withPathParameters(Map.of("id", "ORD-001")), null);

        assertEquals(404, response.getStatusCode());
    }

    @Test
    void secondDeleteOfSameOrderReturns404() {
        handler.handleRequest(delete("ORD-001"), null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(delete("ORD-001"), null);

        assertEquals(404, response.getStatusCode());
    }

    @Test
    void unsupportedMethodReturns405() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                request("PATCH", "/orders/{id}").withPathParameters(Map.of("id", "ORD-001")), null);

        assertEquals(405, response.getStatusCode());
    }

    @Test
    void unexpectedFailureReturns500WithoutInternalDetails() {
        OrderRepository broken = new InMemoryOrderRepository() {
            @Override
            public java.util.Optional<edu.eci.orders.domain.Order> findById(String id) {
                throw new IllegalStateException("database exploded");
            }
        };
        OrdersApiHandler failing = new OrdersApiHandler(new OrderService(broken));

        APIGatewayProxyResponseEvent response = failing.handleRequest(
                request("GET", "/orders/{id}").withPathParameters(Map.of("id", "ORD-001")), null);

        assertEquals(500, response.getStatusCode());
        assertEquals("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Unexpected error\"}", response.getBody());
    }

    private static APIGatewayProxyRequestEvent delete(String id) {
        return request("DELETE", "/orders/{id}").withPathParameters(Map.of("id", id));
    }

    private static APIGatewayProxyRequestEvent request(String method, String resource) {
        return new APIGatewayProxyRequestEvent().withHttpMethod(method).withResource(resource);
    }
}
