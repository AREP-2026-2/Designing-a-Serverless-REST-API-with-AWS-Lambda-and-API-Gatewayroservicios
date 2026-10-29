package edu.eci.orders.application;

import edu.eci.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/** Storage port. The service depends on this interface, not on a concrete store. */
public interface OrderRepository {

    String nextId();

    Order save(Order order);

    Optional<Order> findById(String id);

    List<Order> findByCustomerId(String customerId);

    /** @return true if the order existed and was removed */
    boolean deleteById(String id);
}
