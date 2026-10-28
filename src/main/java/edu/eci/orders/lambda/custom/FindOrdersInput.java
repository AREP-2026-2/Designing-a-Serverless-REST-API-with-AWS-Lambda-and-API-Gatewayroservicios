package edu.eci.orders.lambda.custom;

/** Input built by the API Gateway mapping template for GET /orders?customerId=...: {"customerId": "CUST-42"} */
public class FindOrdersInput {

    private String customerId;

    public FindOrdersInput() {
    }

    public FindOrdersInput(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
