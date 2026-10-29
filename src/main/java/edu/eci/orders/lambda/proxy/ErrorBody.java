package edu.eci.orders.lambda.proxy;

/** JSON error body of the API: {"error": "ORDER_NOT_FOUND", "message": "..."} */
public record ErrorBody(String error, String message) {
}
