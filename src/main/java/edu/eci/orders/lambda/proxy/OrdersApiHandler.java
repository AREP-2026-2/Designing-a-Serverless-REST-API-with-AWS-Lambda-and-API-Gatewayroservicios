package edu.eci.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.orders.application.OrderService;
import edu.eci.orders.domain.CreateOrderRequest;
import edu.eci.orders.domain.Order;
import edu.eci.orders.domain.OrderNotFoundException;
import edu.eci.orders.domain.ValidationException;
import edu.eci.orders.infrastructure.InMemoryOrderRepository;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP adapter for API Gateway Lambda proxy integration. One Lambda serves the
 * whole Orders API.
 *
 * Handler: edu.eci.orders.lambda.proxy.OrdersApiHandler::handleRequest
 *
 * This class is the only place that knows about HTTP: it reads the method,
 * resource, path/query parameters and body from the event, calls OrderService,
 * and chooses status codes, headers and the JSON body of the response.
 */
public class OrdersApiHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final OrderService DEFAULT_SERVICE =
            new OrderService(InMemoryOrderRepository.withSampleData());

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final OrderService service;

    public OrdersApiHandler() {
        this(DEFAULT_SERVICE);
    }

    OrdersApiHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        logEvent(event, context);
        try {
            return route(event);
        } catch (ValidationException e) {
            return error(400, "INVALID_REQUEST", e.getMessage());
        } catch (OrderNotFoundException e) {
            return error(404, "ORDER_NOT_FOUND", e.getMessage());
        } catch (RuntimeException e) {
            // Logged to CloudWatch; the client only gets a generic message.
            e.printStackTrace();
            return error(500, "INTERNAL_ERROR", "Unexpected error");
        }
    }

    /** Writes the received proxy event to CloudWatch, to inspect what API Gateway sends. */
    private static void logEvent(APIGatewayProxyRequestEvent event, Context context) {
        if (context == null) {
            return;
        }
        try {
            context.getLogger().log("EVENT " + JSON.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            context.getLogger().log("EVENT (not serializable) " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent event) {
        String method = event.getHttpMethod();
        String resource = event.getResource();

        if ("/orders".equals(resource)) {
            if ("POST".equals(method)) {
                return createOrder(event);
            }
            if ("GET".equals(method)) {
                return json(200, service.getOrdersByCustomer(param(event.getQueryStringParameters(), "customerId")));
            }
        } else if ("/orders/{id}".equals(resource)) {
            if ("GET".equals(method)) {
                return json(200, service.getOrder(param(event.getPathParameters(), "id")));
            }
        } else {
            return error(404, "NOT_FOUND", "Resource " + event.getPath() + " does not exist");
        }
        return error(405, "METHOD_NOT_ALLOWED", "Method " + method + " is not allowed on " + resource);
    }

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent event) {
        CreateOrderRequest request = parseBody(event.getBody());
        Order order = service.createOrder(request);

        APIGatewayProxyResponseEvent response = json(201, order);
        response.getHeaders().put("Location", collectionPath(event) + "/" + order.getId());
        return response;
    }

    private static CreateOrderRequest parseBody(String body) {
        if (body == null || body.isBlank()) {
            return null; // OrderService rejects a missing request
        }
        try {
            return JSON.readValue(body, CreateOrderRequest.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Request body is not valid JSON");
        }
    }

    /** Public path of /orders including the stage, e.g. "/dev/orders", so Location is resolvable. */
    private static String collectionPath(APIGatewayProxyRequestEvent event) {
        if (event.getRequestContext() != null && event.getRequestContext().getPath() != null) {
            return event.getRequestContext().getPath();
        }
        return "/orders";
    }

    private static String param(Map<String, String> params, String name) {
        return params == null ? null : params.get(name);
    }

    private static APIGatewayProxyResponseEvent error(int status, String code, String message) {
        return json(status, new ErrorBody(code, message));
    }

    private static APIGatewayProxyResponseEvent json(int status, Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(new HashMap<>(Map.of("Content-Type", "application/json")))
                    .withBody(JSON.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
