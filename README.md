# Orders API — Serverless REST API con AWS Lambda y API Gateway

API REST pequeña para gestionar órdenes, construida con Java 17, Maven, AWS Lambda y Amazon API Gateway.

## Cómo compilar y probar

Requisitos: Java 17 y Maven 3.9+.

```bash
mvn test       # ejecuta las pruebas unitarias (no requiere AWS)
mvn package    # genera target/orders-api-1.0.0.jar
```

---

## 1. Diseño del API (contrato)

### Representación de una orden

```json
{
  "id": "ORD-001",
  "customerId": "CUST-42",
  "product": "MacBook Air",
  "quantity": 1
}
```

### Formato de error (igual para todos los errores)

```json
{
  "error": "ORDER_NOT_FOUND",
  "message": "Order ORD-999 does not exist"
}
```

| Código `error`     | HTTP | Cuándo                                                       |
|--------------------|------|--------------------------------------------------------------|
| `INVALID_REQUEST`  | 400  | Cuerpo inválido, campo faltante, `quantity < 1`, parámetro faltante |
| `ORDER_NOT_FOUND`  | 404  | La orden solicitada no existe                                |
| `INTERNAL_ERROR`   | 500  | Error inesperado (no se exponen detalles internos)           |

### Operaciones

#### Crear una orden — `POST /orders`

- **Path params:** ninguno · **Query params:** ninguno
- **Request body:**
  ```json
  { "customerId": "CUST-42", "product": "MacBook Air", "quantity": 1 }
  ```
- **Éxito:** `201 Created`, header `Location: /orders/ORD-004`, cuerpo = la orden creada (con el `id` generado).
- **Errores:** `400 INVALID_REQUEST` (JSON inválido, `customerId`/`product` vacíos, `quantity` faltante o < 1), `500 INTERNAL_ERROR`.

#### Consultar una orden — `GET /orders/{id}`

- **Path params:** `id` (ej. `ORD-001`) · **Query params:** ninguno · **Body:** ninguno
- **Éxito:** `200 OK`, cuerpo = la orden.
- **Errores:** `404 ORDER_NOT_FOUND`, `500 INTERNAL_ERROR`.

#### Buscar órdenes de un cliente — `GET /orders?customerId={customerId}`

- **Path params:** ninguno · **Query params:** `customerId` (obligatorio) · **Body:** ninguno
- **Éxito:** `200 OK`, cuerpo = arreglo JSON de órdenes. Si el cliente no tiene órdenes → `200 OK` con `[]`.
- **Errores:** `400 INVALID_REQUEST` (falta `customerId`), `500 INTERNAL_ERROR`.

> `DELETE /orders/{id}` se agrega en el reto final (sección 17).

### Preguntas de diseño

**¿Cuáles son los recursos del API?**
El recurso principal es la **colección de órdenes** (`/orders`) y cada **orden individual** (`/orders/{id}`). El cliente aparece solo como criterio de filtro, no como recurso propio en esta versión.

**¿Por qué `/orders/{id}` para obtener una orden?**
Porque el `id` identifica de forma única a un recurso: la orden es un elemento de la colección `/orders`, y su URL debe ser estable y única. Un identificador de recurso va en el path, no en un parámetro opcional.

**¿Por qué `customerId` es un query parameter en la búsqueda?**
Porque no identifica un recurso: **filtra** la colección `/orders`. El resultado sigue siendo una colección de órdenes (puede estar vacía). Los query params son el mecanismo natural para filtros opcionales y combinables (ej. `?customerId=...&product=...` en el futuro). La alternativa `/customers/{id}/orders` sería válida si los clientes fueran un recurso del sistema, pero no lo son.

**¿Qué debe retornar `POST /orders` al crear una orden?**
`201 Created`, el cuerpo con la orden creada (incluyendo el `id` asignado por el servidor) y un header `Location` con la URL del nuevo recurso (`/orders/ORD-004`).

**¿Qué debe retornar el API si una orden no existe?**
`404 Not Found` con el cuerpo de error `{"error": "ORDER_NOT_FOUND", "message": "Order ORD-999 does not exist"}`. En cambio, buscar un cliente sin órdenes **no** es 404: la colección filtrada existe y está vacía (`200 []`).

**¿Qué información debe contener una respuesta de error?**
Un código estable y legible por máquinas (`error`), útil para que el cliente tome decisiones, y un mensaje legible por humanos (`message`). No debe incluir stack traces ni detalles internos. El estado HTTP comunica la categoría del error; el código `error` la causa específica.

---

## 2. Parte I — Aplicación en Java puro

### Estructura

```
src/main/java/edu/eci/orders/
├── domain/                 # Conceptos del negocio (Java puro)
│   ├── Order.java
│   ├── CreateOrderRequest.java
│   ├── OrderNotFoundException.java
│   └── ValidationException.java
├── application/            # Lógica de la aplicación (Java puro)
│   ├── OrderService.java       # createOrder, getOrder, getOrdersByCustomer
│   └── OrderRepository.java    # interfaz de almacenamiento
└── infrastructure/
    └── InMemoryOrderRepository.java   # almacenamiento en memoria (no persistente)
```

- `OrderService` valida la entrada y lanza **excepciones de dominio** (`ValidationException`, `OrderNotFoundException`). No sabe nada de códigos HTTP: traducir esas excepciones a 400/404 es responsabilidad de la capa HTTP (API Gateway o el adaptador).
- `InMemoryOrderRepository.withSampleData()` precarga `ORD-001`, `ORD-002` (CUST-42) y `ORD-003` (CUST-7), para que las lecturas funcionen en una Lambda recién iniciada. **Los datos en memoria no son persistentes**: se pierden cuando Lambda recicla el entorno de ejecución, y cada función Lambda tiene su propia copia.

### Checkpoint 1

Respuestas al terminar la Parte I (antes de agregar los handlers de Lambda).

**¿Se puede probar la funcionalidad sin AWS?** Sí. `mvn test` ejecuta 21 pruebas JUnit sobre `OrderService` sin ninguna dependencia de AWS ni red.

**¿La aplicación requiere un servidor web?** No. Es una librería de clases Java; se invoca con llamadas a métodos.

**¿Requiere Spring Boot?** No. La única dependencia es JUnit, y solo en scope `test`.

**¿Qué contiene el JAR generado?** Solo las clases compiladas del proyecto (`domain`, `application`, `infrastructure`) y metadatos de Maven (`META-INF/MANIFEST.MF`, `pom.xml`, `pom.properties`). No incluye dependencias ni handlers de Lambda (todavía).

**¿Qué partes de la aplicación conocen HTTP actualmente?** Ninguna. No hay requests, responses, headers ni códigos de estado en el código.

---

## 3. Parte II — Funciones Java en AWS Lambda

No rediseñé la aplicación para Lambda. Agregué un paquete nuevo, `edu.eci.orders.lambda.custom`, con un handler delgado por operación que solo recibe el objeto de entrada, llama a `OrderService` y devuelve el resultado:

| Lambda              | Handler                                                               | Entrada (JSON)                                   | Salida (JSON)    |
|---------------------|-----------------------------------------------------------------------|--------------------------------------------------|------------------|
| `orders-create`     | `edu.eci.orders.lambda.custom.CreateOrderHandler::handleRequest`      | `{"customerId":"CUST-42","product":"MacBook Air","quantity":1}` | orden creada     |
| `orders-get`        | `edu.eci.orders.lambda.custom.GetOrderHandler::handleRequest`         | `{"id":"ORD-001"}`                               | orden            |
| `orders-find`       | `edu.eci.orders.lambda.custom.FindOrdersByCustomerHandler::handleRequest` | `{"customerId":"CUST-42"}`                   | arreglo de órdenes |

Cada handler implementa `RequestHandler<Entrada, Salida>` de `aws-lambda-java-core`; es la única dependencia de AWS del proyecto en este punto. El JAR se empaqueta con `maven-shade-plugin` para incluir esa dependencia.

**Configuración de las funciones (AWS Academy Learner Lab):** runtime *Java 17*, arquitectura *x86_64*, rol de ejecución existente `LabRole` (el laboratorio no permite crear roles), memoria 512 MB, timeout 15 s, código: `target/orders-api-1.0.0.jar`.

**Evidencia: creación y configuración de `orders-create`**

*Entorno AWS Academy Learner Lab iniciado*

![Entorno AWS Academy Learner Lab iniciado](docs/evidence/part2/00-learner-lab.png)

*Función creada (Java 17, LabRole)*

![Función creada (Java 17, LabRole)](docs/evidence/part2/01-function-created.png)

*Carga del JAR orders-api-1.0.0.jar*

![Carga del JAR orders-api-1.0.0.jar](docs/evidence/part2/02-upload-jar.png)

*Configuración del handler*

![Configuración del handler](docs/evidence/part2/03-handler-config.png)

*Memoria 512 MB y timeout 15 s*

![Memoria 512 MB y timeout 15 s](docs/evidence/part2/04-memory-timeout.png)

**Errores:** los handlers traducen las excepciones de dominio a una `OrderApiException` cuyo mensaje es el cuerpo de error en JSON (`{"error":"ORDER_NOT_FOUND","message":"..."}`). El handler **no** elige códigos HTTP; eso queda para API Gateway (Parte IV).

### Diagrama de la solución (Checkpoint 2)

```mermaid
flowchart TD
    J1["JSON de entrada<br/>{customerId, product, quantity}"] --> RT1
    subgraph AWS["AWS Lambda (orders-create)"]
        RT1["Runtime de Java 17<br/>deserializa JSON → CreateOrderRequest"] --> H["CreateOrderHandler<br/>(adaptador, depende de aws-lambda-java-core)"]
        H --> S["OrderService<br/>(Java puro)"]
        S --> R["InMemoryOrderRepository<br/>(Java puro, en memoria)"]
        S --> H
        H --> RT2["Runtime de Java 17<br/>serializa Order → JSON"]
    end
    RT2 --> J2["JSON de salida<br/>{id, customerId, product, quantity}"]
```

### Investigación: ¿cómo convierte Lambda JSON ↔ objetos Java?

1. En la configuración de la función, el **handler** (`paquete.Clase::metodo`) le indica al runtime qué clase instanciar y qué método invocar. El runtime crea la instancia con el constructor sin argumentos **una vez por entorno de ejecución** y la reutiliza en las invocaciones "warm" (por eso el `OrderService` estático conserva su estado entre invocaciones mientras el entorno siga vivo).
2. Por reflexión, el runtime lee el tipo genérico declarado en `RequestHandler<CreateOrderRequest, Order>` para saber a qué clase convertir la entrada.
3. Deserializa el JSON del evento con su serializador interno (basado en Jackson) siguiendo las convenciones de JavaBeans: crea el objeto con el constructor sin argumentos y asigna cada propiedad con su *setter*. Por eso `CreateOrderRequest` y `Order` son POJOs con constructor vacío y getters/setters.
4. Al terminar, serializa el valor de retorno a JSON usando los *getters*. Una `List<Order>` se convierte en un arreglo JSON.
5. Si el método lanza una excepción, el runtime responde con un JSON de error con la forma `{"errorMessage": ..., "errorType": ..., "stackTrace": [...]}`. `errorMessage` es el mensaje de la excepción.
6. Si se necesita control total del formato, se puede usar `RequestStreamHandler`, que recibe `InputStream`/`OutputStream` sin conversión automática.

### Verificación contra la Lambda desplegada (`orders-create`)

| # | Entrada | Resultado | Qué demuestra |
|---|---------|-----------|---------------|
| 1 | `{"customerId":"CUST-42","product":"MacBook Air","quantity":1}` | `{"id":"ORD-004","customerId":"CUST-42","product":"MacBook Air","quantity":1}` | El JSON se convierte en `CreateOrderRequest` y el `Order` retornado vuelve a JSON. Es `ORD-004` porque los datos de ejemplo ocupan `ORD-001`…`ORD-003`. Duración 61.83 ms, facturada 442 ms (arranque en frío: carga de la JVM y de las clases). |
| 2 | `{"customerId":"CUST-100","product":"Monitor","quantity":3}` | `{"id":"ORD-005",...}` | El entorno se reutilizó (invocación *warm*): el contador en memoria continuó en 5. |
| 3 | `{"customerId":"CUST-42","product":"Mouse","quantity":0}` | Error: `errorMessage` = `{"error":"INVALID_REQUEST","message":"quantity must be greater than 0"}`, `errorType` = `edu.eci.orders.lambda.custom.OrderApiException`, más `stackTrace` | Una excepción del handler se convierte en el JSON de error del runtime; `errorMessage` es el mensaje de la excepción. |
| 4 | `{"customerId":"CUST-42","product":"Mouse","quantity":1,"color":"red"}` | `{"id":"ORD-006",...}` | Las propiedades desconocidas se **ignoran**; no hacen fallar la deserialización. Duración 1.86 ms (warm). |
| 5 | `{"customerId":"CUST-42","product":"Mouse","quantity":"abc"}` | Error: `errorMessage` = `"An error occurred during JSON parsing"`, `errorType` = `java.lang.RuntimeException`, `stackTrace` = `[]` | La conversión ocurre **antes** de llamar al handler: con un tipo incompatible, el runtime falla al parsear y el código Java nunca se ejecuta (la traza está vacía y no aparece `CreateOrderHandler`). |

**Evidencia: invocaciones manuales de `orders-create`**

*1. CUST-42 → ORD-004*

![1. CUST-42 → ORD-004](docs/evidence/part2/05-invoke-cust42.png)

*2. CUST-100 → ORD-005 (entorno reutilizado)*

![2. CUST-100 → ORD-005 (entorno reutilizado)](docs/evidence/part2/06-invoke-cust100.png)

*3. quantity = 0 → INVALID_REQUEST*

![3. quantity = 0 → INVALID_REQUEST](docs/evidence/part2/07-invoke-quantity-0.png)

*4. Propiedad desconocida "color" → ignorada*

![4. Propiedad desconocida "color" → ignorada](docs/evidence/part2/08-invoke-extra-field.png)

*5. quantity = "abc" → error de parsing del runtime*

![5. quantity = "abc" → error de parsing del runtime](docs/evidence/part2/09-invoke-wrong-type.png)

### Conclusión

El runtime de Java de Lambda actúa como un adaptador de serialización entre el mundo JSON y el método Java. Usa la firma genérica del handler para saber a qué tipo convertir la entrada, deserializa con convenciones de JavaBeans (ignorando propiedades desconocidas), invoca el método y serializa el valor de retorno con sus getters. Hay dos tipos de error distintos:

- **Errores del runtime** (JSON con forma incorrecta): se producen antes de llegar a nuestro código y devuelven un mensaje genérico (`An error occurred during JSON parsing`).
- **Errores de nuestro código** (excepciones): el runtime los serializa como `errorMessage`/`errorType`/`stackTrace`.

Como el `stackTrace` expone detalles internos, la respuesta cruda de Lambda no debe llegar tal cual a un cliente HTTP. Ese filtrado es una de las responsabilidades que se asignan a API Gateway en la Parte IV.

La reutilización del entorno (ORD-004 → 005 → 006) confirma que el estado en memoria sobrevive entre invocaciones *warm*. Sin embargo, no es persistente: se pierde cuando Lambda recicla el entorno y no se comparte entre funciones.

### Observación: el handler se resuelve en tiempo de ejecución

Al crear `orders-get`, la primera invocación falló con `ClassNotFoundException: edu.eci.orders.lambda.custom.GetOrderHandler`. El SHA-256 del código (`o0czLMg+...`) era el del código de ejemplo que Lambda crea por defecto: el JAR no se había subido. Después de subirlo, el SHA-256 pasó a `ssSHPxDoN7Qk...` (el mismo de `orders-create`) y la función respondió correctamente.

Esto confirma que Lambda **no valida el handler al guardar la configuración**: carga la clase por reflexión en la primera invocación (arranque en frío). Un error en el nombre del handler o en el empaquetado solo se detecta al invocar la función.

**Evidencia: creación de `orders-get` y `ClassNotFoundException`**

*Función orders-get creada*

![Función orders-get creada](docs/evidence/part2/10-orders-get-created.png)

*Handler GetOrderHandler configurado*

![Handler GetOrderHandler configurado](docs/evidence/part2/11-orders-get-handler.png)

*ClassNotFoundException (JAR no cargado, SHA-256 del código por defecto)*

![ClassNotFoundException (JAR no cargado, SHA-256 del código por defecto)](docs/evidence/part2/12-orders-get-class-not-found.png)

| Lambda | Entrada | Resultado |
|---|---|---|
| `orders-get` | `{"id":"ORD-001"}` | `{"id":"ORD-001","customerId":"CUST-42","product":"MacBook Air","quantity":1}` |
| `orders-get` | `{"id":"ORD-999"}` | Error: `errorMessage` = `{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}` |
| `orders-find` | `{"customerId":"CUST-42"}` | `[{"id":"ORD-001",...,"product":"MacBook Air"},{"id":"ORD-002",...,"product":"Magic Mouse"}]` |
| `orders-find` | `{"customerId":"CUST-100"}` | `[]` |
| `orders-find` | `{"customerId":""}` | Error: `errorMessage` = `{"error":"INVALID_REQUEST","message":"customerId is required"}` |

**Evidencia: invocaciones de `orders-get` y `orders-find`**

*orders-get: ORD-001*

![orders-get: ORD-001](docs/evidence/part2/13-orders-get-ord-001.png)

*orders-get: ORD-999 → ORDER_NOT_FOUND*

![orders-get: ORD-999 → ORDER_NOT_FOUND](docs/evidence/part2/14-orders-get-ord-999.png)

*orders-find: CUST-42 → 2 órdenes*

![orders-find: CUST-42 → 2 órdenes](docs/evidence/part2/15-orders-find-cust42.png)

*orders-find: CUST-100 → []*

![orders-find: CUST-100 → []](docs/evidence/part2/16-orders-find-cust100.png)

*orders-find: customerId vacío → INVALID_REQUEST*

![orders-find: customerId vacío → INVALID_REQUEST](docs/evidence/part2/17-orders-find-blank.png)

`orders-find` devuelve `[]` para `CUST-100` aunque `orders-create` ya había creado `ORD-005` para ese cliente. Cada función Lambda tiene su propio entorno de ejecución y, por lo tanto, su propia copia del repositorio en memoria. Con una Lambda por operación, el estado no se comparte entre operaciones.

---

## 4. Partes III y IV — API Gateway con integración no-proxy (Arquitectura A)

API REST `orders-api-mapping` (Regional) en API Gateway, con integración Lambda **sin proxy**: API Gateway transforma la petición HTTP en el JSON exacto que espera cada handler, y transforma la respuesta de Lambda en una respuesta HTTP.

Las plantillas de mapeo usadas están en [apigateway/architecture-a/](apigateway/architecture-a/).

### `GET /orders/{id}` → `orders-get`

**Solicitud de integración**, plantilla `application/json` ([get-order.request.vtl](apigateway/architecture-a/get-order.request.vtl)):

```
{
  "id": "$util.escapeJavaScript($input.params('id'))"
}
```

`$input.params('id')` toma el valor del parámetro de ruta `{id}`, y API Gateway construye `{"id":"ORD-001"}`. El handler nunca ve la petición HTTP, solo el identificador.

**Evidencia: recurso `/orders/{id}` con método GET (proxy desactivado)**

*Recursos /orders y /{id} con método GET*

![Recursos /orders y /{id} con método GET](docs/evidence/part4/01-get-order-method-created.png)

*Pantalla Solicitud de método: aquí no están las plantillas de mapeo (van en Solicitud de integración)*

![Pantalla Solicitud de método: aquí no están las plantillas de mapeo (van en Solicitud de integración)](docs/evidence/part4/01b-method-request-screen.png)

**Hallazgo: sin mapeo de errores, todo es 200.** Con solo la plantilla de solicitud configurada:

| Petición | Estado | Cuerpo |
|---|---|---|
| `GET /orders/ORD-001` | 200 | `{"id":"ORD-001","customerId":"CUST-42","product":"MacBook Air","quantity":1}` |
| `GET /orders/ORD-999` | **200** | `{"errorMessage":"{\"error\":\"ORDER_NOT_FOUND\",...}","errorType":"...OrderApiException","stackTrace":[...]}` |

**Evidencia: prueba de `GET /orders/{id}` antes de mapear errores**

*ORD-001 → 200*

![ORD-001 → 200](docs/evidence/part4/02-get-ord-001-200.png)

*ORD-999 → 200 con la respuesta cruda de Lambda (stackTrace incluido)*

![ORD-999 → 200 con la respuesta cruda de Lambda (stackTrace incluido)](docs/evidence/part4/03-get-ord-999-unmapped-200.png)

En la integración no-proxy, API Gateway responde con la *respuesta de integración por defecto* (200) a menos que una expresión regular sobre `errorMessage` indique otra cosa. Además, se filtra al cliente el `stackTrace` interno. Por eso el mapeo de errores a códigos HTTP es una responsabilidad explícita que hay que configurar en API Gateway, ruta por ruta.

**Mapeo de errores para `GET /orders/{id}`:**

1. *Respuesta de método*: se declara el código `404` (modelo `Empty`, `application/json`).
2. *Respuesta de integración*: expresión regular `.*"error":"ORDER_NOT_FOUND".*` sobre el `errorMessage` de Lambda → estado `404`, con la plantilla [error.response.vtl](apigateway/architecture-a/error.response.vtl), que devuelve solo el `errorMessage` (nuestro JSON de error) y descarta `errorType` y `stackTrace`.

Con solo el paso de la regex, el estado ya era 404, pero el cuerpo seguía siendo la respuesta cruda de Lambda. El **código de estado** y el **cuerpo** de la respuesta se configuran por separado en API Gateway.

| Petición | Estado | Cuerpo |
|---|---|---|
| `GET /orders/ORD-999` | 404 | `{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}` |

**Evidencia: mapeo del 404**

*Respuesta de integración: regex ORDER_NOT_FOUND → 404*

![Respuesta de integración: regex ORDER_NOT_FOUND → 404](docs/evidence/part4/04a-integration-response-404-regex.png)

*Regex configurada, sin plantilla: 404 pero con cuerpo crudo*

![Regex configurada, sin plantilla: 404 pero con cuerpo crudo](docs/evidence/part4/04-get-ord-999-404-without-template.png)

*Regex + plantilla: 404 con el error en el formato del API*

![Regex + plantilla: 404 con el error en el formato del API](docs/evidence/part4/05-get-ord-999-404-mapped.png)

**Despliegue:** etapa `dev`, URL de invocación `https://ggo6458fw1.execute-api.us-east-1.amazonaws.com/dev`.

```
$ curl -i https://ggo6458fw1.execute-api.us-east-1.amazonaws.com/dev/orders/ORD-001
HTTP/1.1 200 OK
Content-Type: application/json

{"id":"ORD-001","customerId":"CUST-42","product":"MacBook Air","quantity":1}

$ curl -i https://ggo6458fw1.execute-api.us-east-1.amazonaws.com/dev/orders/ORD-999
HTTP/1.1 404 Not Found
Content-Type: application/json

{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}
```

**Evidencia: etapa `dev`**

*Etapa dev y URL de invocación*

![Etapa dev y URL de invocación](docs/evidence/part4/06-stage-dev-invoke-url.png)

`GET /orders/ORD-004` también devuelve 404, aunque `orders-create` creó esa orden en la Parte II: la orden vive en la memoria de otra función Lambda.

### `GET /orders?customerId={customerId}` → `orders-find`

- *Solicitud de método*: se declara el parámetro de consulta `customerId`, sin marcarlo como obligatorio. La validación queda en `OrderService` para que el error tenga el mismo formato que el resto del API.
- *Solicitud de integración*, plantilla `application/json` ([find-orders.request.vtl](apigateway/architecture-a/find-orders.request.vtl)):
  ```
  {
    "customerId": "$util.escapeJavaScript($input.params('customerId'))"
  }
  ```
  `$input.params('customerId')` busca el nombre en la ruta, la query string y los headers. Si no existe, devuelve una cadena vacía, y `OrderService` la rechaza.
- *Respuesta de método*: `400`.
- *Respuesta de integración*: regex `.*"error":"INVALID_REQUEST".*` → `400`, con la plantilla [error.response.vtl](apigateway/architecture-a/error.response.vtl).

En la consola, una *Respuesta de integración* solo se puede crear para códigos ya declarados en la *Respuesta de método* ("No hay códigos de estado de respuesta de método disponibles"). Primero se declara qué puede responder el API y después se define cuándo.

| Petición | Estado | Cuerpo |
|---|---|---|
| `GET /orders?customerId=CUST-42` | 200 | `[{"id":"ORD-001",...,"product":"MacBook Air",...},{"id":"ORD-002",...,"product":"Magic Mouse",...}]` |
| `GET /orders?customerId=CUST-100` | 200 | `[]` |
| `GET /orders` (sin `customerId`) | 400 | `{"error":"INVALID_REQUEST","message":"customerId is required"}` |

**Evidencia: `GET /orders?customerId=`**

*Sin códigos declarados en Respuesta de método no se puede crear la Respuesta de integración*

![Sin códigos declarados en Respuesta de método no se puede crear la Respuesta de integración](docs/evidence/part4/07-no-method-response-codes.png)

*customerId=CUST-42 → 200*

![customerId=CUST-42 → 200](docs/evidence/part4/08-find-cust42-200.png)

*customerId=CUST-100 → 200 []*

![customerId=CUST-100 → 200 []](docs/evidence/part4/09-find-cust100-200-empty.png)

*Sin customerId → 400 INVALID_REQUEST*

![Sin customerId → 400 INVALID_REQUEST](docs/evidence/part4/10-find-missing-customer-400.png)

**Cambios sin implementar:** antes de volver a implementar la etapa, `GET /orders?customerId=CUST-42` en la URL pública respondía `403 {"message":"Missing Authentication Token"}`. API Gateway da esa respuesta para cualquier ruta o método que no existe en la etapa desplegada. Los cambios de configuración solo se publican al implementar el API en una etapa.
