package edu.eci.orders.domain;

/** The input violates a business rule (missing customer, quantity &lt; 1, ...). */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
