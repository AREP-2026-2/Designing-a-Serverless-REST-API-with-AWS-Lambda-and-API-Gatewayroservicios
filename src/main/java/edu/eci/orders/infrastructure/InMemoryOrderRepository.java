package edu.eci.orders.infrastructure;

import edu.eci.orders.application.OrderRepository;
import edu.eci.orders.domain.Order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory store. NOT persistent: on Lambda, data lives only as long as the
 * execution environment, and each Lambda function has its own copy.
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    /** Repository preloaded with predefined orders, so reads work on a fresh Lambda. */
    public static InMemoryOrderRepository withSampleData() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        repository.save(new Order(repository.nextId(), "CUST-42", "MacBook Air", 1));
        repository.save(new Order(repository.nextId(), "CUST-42", "Magic Mouse", 2));
        repository.save(new Order(repository.nextId(), "CUST-7", "Keyboard", 2));
        return repository;
    }

    @Override
    public String nextId() {
        return String.format("ORD-%03d", sequence.incrementAndGet());
    }

    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        List<Order> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.getCustomerId().equals(customerId)) {
                result.add(order);
            }
        }
        result.sort(Comparator.comparing(Order::getId));
        return result;
    }

    @Override
    public boolean deleteById(String id) {
        return orders.remove(id) != null;
    }
}
