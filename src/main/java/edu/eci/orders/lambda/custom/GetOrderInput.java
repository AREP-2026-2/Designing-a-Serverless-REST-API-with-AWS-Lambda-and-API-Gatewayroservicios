package edu.eci.orders.lambda.custom;

/** Input built by the API Gateway mapping template for GET /orders/{id}: {"id": "ORD-001"} */
public class GetOrderInput {

    private String id;

    public GetOrderInput() {
    }

    public GetOrderInput(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
