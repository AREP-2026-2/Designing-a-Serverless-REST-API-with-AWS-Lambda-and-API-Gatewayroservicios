package edu.eci.orders.application;

import edu.eci.orders.domain.CreateOrderRequest;
import edu.eci.orders.domain.Order;
import edu.eci.orders.domain.OrderNotFoundException;
import edu.eci.orders.domain.ValidationException;

import java.util.List;

/**
 * Application logic of the Orders API. Knows about orders, customers, products
 * and quantities; knows nothing about HTTP, status codes or API Gateway.
 */
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(CreateOrderRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        requireText(request.getCustomerId(), "customerId");
        requireText(request.getProduct(), "product");
        if (request.getQuantity() == null) {
            throw new ValidationException("quantity is required");
        }
        if (request.getQuantity() < 1) {
            throw new ValidationException("quantity must be greater than 0");
        }

        Order order = new Order(
                repository.nextId(),
                request.getCustomerId().trim(),
                request.getProduct().trim(),
                request.getQuantity());
        return repository.save(order);
    }

    public Order getOrder(String id) {
        requireText(id, "id");
        return repository.findById(id.trim())
                .orElseThrow(() -> new OrderNotFoundException(id.trim()));
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        requireText(customerId, "customerId");
        return repository.findByCustomerId(customerId.trim());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
    }
}
