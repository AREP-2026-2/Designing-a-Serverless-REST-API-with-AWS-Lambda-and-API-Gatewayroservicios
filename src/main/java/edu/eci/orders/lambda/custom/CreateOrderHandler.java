package edu.eci.orders.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import edu.eci.orders.application.OrderService;
import edu.eci.orders.domain.CreateOrderRequest;
import edu.eci.orders.domain.Order;
import edu.eci.orders.infrastructure.InMemoryOrderRepository;

/**
 * Lambda handler: edu.eci.orders.lambda.custom.CreateOrderHandler::handleRequest
 * Input  JSON: {"customerId": "...", "product": "...", "quantity": 1}
 * Output JSON: the created order.
 */
public class CreateOrderHandler implements RequestHandler<CreateOrderRequest, Order> {

    // Created once per execution environment and reused by warm invocations.
    private static final OrderService DEFAULT_SERVICE =
            new OrderService(InMemoryOrderRepository.withSampleData());

    private final OrderService service;

    public CreateOrderHandler() {
        this(DEFAULT_SERVICE);
    }

    CreateOrderHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public Order handleRequest(CreateOrderRequest request, Context context) {
        return Errors.translate(() -> service.createOrder(request));
    }
}
