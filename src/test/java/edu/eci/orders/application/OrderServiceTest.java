package edu.eci.orders.application;

import edu.eci.orders.domain.CreateOrderRequest;
import edu.eci.orders.domain.Order;
import edu.eci.orders.domain.OrderNotFoundException;
import edu.eci.orders.domain.ValidationException;
import edu.eci.orders.infrastructure.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(new InMemoryOrderRepository());
    }

    @Test
    void createsValidOrderWithGeneratedId() {
        Order order = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertNotNull(order.getId());
        assertTrue(order.getId().startsWith("ORD-"));
        assertEquals("CUST-42", order.getCustomerId());
        assertEquals("MacBook Air", order.getProduct());
        assertEquals(1, order.getQuantity());
    }

    @Test
    void createdOrdersGetDistinctIds() {
        Order first = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));
        Order second = service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 1));

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void retrievesExistingOrder() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-7", "Keyboard", 2));

        Order found = service.getOrder(created.getId());

        assertEquals(created, found);
    }

    @Test
    void missingOrderThrowsNotFound() {
        OrderNotFoundException e = assertThrows(OrderNotFoundException.class,
                () -> service.getOrder("ORD-999"));

        assertEquals("ORD-999", e.getOrderId());
        assertEquals("Order ORD-999 does not exist", e.getMessage());
    }

    @Test
    void findsOnlyOrdersOfTheGivenCustomer() {
        service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));
        service.createOrder(new CreateOrderRequest("CUST-7", "Keyboard", 2));
        service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 3));

        List<Order> orders = service.getOrdersByCustomer("CUST-42");

        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.getCustomerId().equals("CUST-42")));
    }

    @Test
    void customerWithoutOrdersReturnsEmptyList() {
        assertTrue(service.getOrdersByCustomer("CUST-100").isEmpty());
    }

    @Test
    void rejectsNullRequest() {
        assertThrows(ValidationException.class, () -> service.createOrder(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingCustomerId(String customerId) {
        assertThrows(ValidationException.class,
                () -> service.createOrder(new CreateOrderRequest(customerId, "MacBook Air", 1)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingProduct(String product) {
        assertThrows(ValidationException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", product, 1)));
    }

    @Test
    void rejectsMissingQuantity() {
        assertThrows(ValidationException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", null)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantity(int quantity) {
        assertThrows(ValidationException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", quantity)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsBlankIdOnRetrieval(String id) {
        assertThrows(ValidationException.class, () -> service.getOrder(id));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsBlankCustomerIdOnSearch(String customerId) {
        assertThrows(ValidationException.class, () -> service.getOrdersByCustomer(customerId));
    }

    @Test
    void sampleDataIsAvailableOnFreshRepository() {
        OrderService seeded = new OrderService(InMemoryOrderRepository.withSampleData());

        assertEquals("MacBook Air", seeded.getOrder("ORD-001").getProduct());
        assertEquals(2, seeded.getOrdersByCustomer("CUST-42").size());
    }
}
