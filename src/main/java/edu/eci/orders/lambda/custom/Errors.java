package edu.eci.orders.lambda.custom;

import edu.eci.orders.domain.OrderNotFoundException;
import edu.eci.orders.domain.ValidationException;

import java.util.function.Supplier;

/** Translates domain exceptions into error codes. Choosing the HTTP status is left to API Gateway. */
final class Errors {

    private Errors() {
    }

    static <T> T translate(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ValidationException e) {
            throw new OrderApiException("INVALID_REQUEST", e.getMessage());
        } catch (OrderNotFoundException e) {
            throw new OrderApiException("ORDER_NOT_FOUND", e.getMessage());
        } catch (RuntimeException e) {
            // Logged to CloudWatch; the client only gets a generic message.
            e.printStackTrace();
            throw new OrderApiException("INTERNAL_ERROR", "Unexpected error");
        }
    }
}
