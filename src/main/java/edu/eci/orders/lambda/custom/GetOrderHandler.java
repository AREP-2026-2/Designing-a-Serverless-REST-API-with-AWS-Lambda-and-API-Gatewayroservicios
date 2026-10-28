package edu.eci.orders.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import edu.eci.orders.application.OrderService;
import edu.eci.orders.domain.Order;
import edu.eci.orders.infrastructure.InMemoryOrderRepository;

/**
 * Lambda handler: edu.eci.orders.lambda.custom.GetOrderHandler::handleRequest
 * Input  JSON: {"id": "ORD-001"}
 * Output JSON: the order.
 */
public class GetOrderHandler implements RequestHandler<GetOrderInput, Order> {

    private static final OrderService DEFAULT_SERVICE =
            new OrderService(InMemoryOrderRepository.withSampleData());

    private final OrderService service;

    public GetOrderHandler() {
        this(DEFAULT_SERVICE);
    }

    GetOrderHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public Order handleRequest(GetOrderInput input, Context context) {
        String id = input == null ? null : input.getId();
        return Errors.translate(() -> service.getOrder(id));
    }
}
