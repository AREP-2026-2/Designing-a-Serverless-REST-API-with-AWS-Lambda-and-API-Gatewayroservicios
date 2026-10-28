package edu.eci.orders.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import edu.eci.orders.application.OrderService;
import edu.eci.orders.domain.Order;
import edu.eci.orders.infrastructure.InMemoryOrderRepository;

import java.util.List;

/**
 * Lambda handler: edu.eci.orders.lambda.custom.FindOrdersByCustomerHandler::handleRequest
 * Input  JSON: {"customerId": "CUST-42"}
 * Output JSON: array of orders (possibly empty).
 */
public class FindOrdersByCustomerHandler implements RequestHandler<FindOrdersInput, List<Order>> {

    private static final OrderService DEFAULT_SERVICE =
            new OrderService(InMemoryOrderRepository.withSampleData());

    private final OrderService service;

    public FindOrdersByCustomerHandler() {
        this(DEFAULT_SERVICE);
    }

    FindOrdersByCustomerHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public List<Order> handleRequest(FindOrdersInput input, Context context) {
        String customerId = input == null ? null : input.getCustomerId();
        return Errors.translate(() -> service.getOrdersByCustomer(customerId));
    }
}
