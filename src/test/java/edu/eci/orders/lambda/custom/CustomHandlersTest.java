package edu.eci.orders.lambda.custom;

import edu.eci.orders.application.OrderService;
import edu.eci.orders.domain.CreateOrderRequest;
import edu.eci.orders.domain.Order;
import edu.eci.orders.infrastructure.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomHandlersTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(InMemoryOrderRepository.withSampleData());
    }

    @Test
    void createHandlerReturnsCreatedOrder() {
        Order order = new CreateOrderHandler(service)
                .handleRequest(new CreateOrderRequest("CUST-42", "MacBook Air", 1), null);

        assertEquals("ORD-004", order.getId());
    }

    @Test
    void createHandlerReportsInvalidRequestAsJsonError() {
        OrderApiException e = assertThrows(OrderApiException.class, () -> new CreateOrderHandler(service)
                .handleRequest(new CreateOrderRequest("CUST-42", "MacBook Air", 0), null));

        assertEquals("INVALID_REQUEST", e.getError());
        assertEquals("{\"error\":\"INVALID_REQUEST\",\"message\":\"quantity must be greater than 0\"}",
                e.getMessage());
    }

    @Test
    void getHandlerReturnsExistingOrder() {
        Order order = new GetOrderHandler(service).handleRequest(new GetOrderInput("ORD-003"), null);

        assertEquals("Keyboard", order.getProduct());
    }

    @Test
    void getHandlerReportsMissingOrderAsJsonError() {
        OrderApiException e = assertThrows(OrderApiException.class,
                () -> new GetOrderHandler(service).handleRequest(new GetOrderInput("ORD-999"), null));

        assertEquals("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}",
                e.getMessage());
    }

    @Test
    void getHandlerTreatsNullInputAsInvalid() {
        OrderApiException e = assertThrows(OrderApiException.class,
                () -> new GetOrderHandler(service).handleRequest(null, null));

        assertEquals("INVALID_REQUEST", e.getError());
    }

    @Test
    void findHandlerReturnsCustomerOrders() {
        List<Order> orders = new FindOrdersByCustomerHandler(service)
                .handleRequest(new FindOrdersInput("CUST-42"), null);

        assertEquals(2, orders.size());
    }

    @Test
    void findHandlerReportsMissingCustomerIdAsInvalid() {
        OrderApiException e = assertThrows(OrderApiException.class,
                () -> new FindOrdersByCustomerHandler(service).handleRequest(new FindOrdersInput(""), null));

        assertEquals("INVALID_REQUEST", e.getError());
    }
}
