package edu.eci.orders.domain;

/** The requested order does not exist. Says nothing about HTTP 404: that mapping lives elsewhere. */
public class OrderNotFoundException extends RuntimeException {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order " + orderId + " does not exist");
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
