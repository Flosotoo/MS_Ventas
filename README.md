# Microservicio Ventas

## Descripción

Microservicio de gestión de ventas presenciales, devoluciones y retiro de pedidos web para Perfulandia SPA. Registra ventas en tienda, calcula impuestos y descuentos, descuenta stock, procesa devoluciones con reingreso de stock y cierra los pedidos web cuando el cliente los retira en tienda.

- Historias de usuario: HU-26, HU-28, HU-40 y HU-55.
- Swagger/OpenAPI disponible en: <http://localhost:8095/swagger-ui.html>

## Estudiante

Florencia Soto

## Tecnologías

- Java 25, Spring Boot 4.x, JPA/Hibernate, Bean Validation
- MySQL 8.x (para Duoc/XAMPP)
- Comunicación entre microservicios vía RestTemplate (consume MS Productos y Stock, MS Sucursales, MS Clientes y MS Envíos)
- Maven, Swagger/OpenAPI (springdoc)

El `RestTemplate` se configura con timeouts de conexión y lectura de 3 segundos (`RestTemplateConfig`), lo que habilita la **degradación elegante** de las validaciones externas.

## Microservicios que consume

Este MS se comunica con otros microservicios vía REST para validar datos y operar el stock:

| MS destino | Puerto | Para qué |
| ---------- | ------ | -------- |
| MS Productos y Stock | 8082 | Validar que el producto exista, consultar disponibilidad, descontar/reingresar stock y **confirmar reservas** de pedidos web |
| MS Sucursales y Logística | 8087 | Validar que la sucursal de la venta exista |
| MS Clientes | 8081 | Validar el cliente cuando la venta está asociada a uno (retiro web) |
| MS Envíos | 8091 | Leer el pedido para materializar el retiro y marcarlo como RETIRADO |

Las validaciones de cliente, pedido y sucursal usan **degradación elegante**: si el MS externo está caído (timeout), la operación continúa con una advertencia en el log en vez de fallar. Las validaciones de cliente y pedido además son **condicionales**: solo ocurren si la venta trae esos datos (en una venta presencial anónima, `idCliente` e `idPedido` son null y se omiten).

> **Nota sobre el stock del retiro web:** un pedido web reserva stock al momento de crearse. Por eso el retiro en tienda **no descuenta stock de nuevo**, sino que **confirma la reserva** en MS Productos (`/api/inventario/confirmar-reserva`). El descuento directo (`/api/inventario/ajustar`) se usa solo en la venta presencial.

## Endpoints

### Ventas

| Método | Ruta | HU | Descripción |
| ------ | ---- | -- | ----------- |
| POST | `/api/ventas` | HU-26 | Registrar venta presencial (calcula IVA, total y descuenta stock) |
| POST | `/api/ventas/retiro/{idPedido}` | HU-55 | Materializar como venta un pedido web con retiro en tienda (los datos se leen del pedido, no del cuerpo) |
| GET | `/api/ventas` | — | Listar ventas (filtro opcional por `?idSucursal=`) |
| GET | `/api/ventas/{id}` | — | Obtener una venta por id |
| GET | `/api/ventas/por-pedido/{idPedido}` | — | Obtener la venta asociada a un pedido web |
| PUT | `/api/ventas/{id}/descuento` | HU-28 | Cambiar el descuento de una venta y recalcular IVA y total |
| DELETE | `/api/ventas/{id}` | — | Anular una venta presencial (reingresa el stock vendido) |

### Devoluciones

| Método | Ruta | HU | Descripción |
| ------ | ---- | -- | ----------- |
| POST | `/api/devoluciones` | HU-40 | Procesar una devolución (reingresa stock, valida cantidades) |
| GET | `/api/devoluciones` | — | Listar devoluciones (filtro opcional por `?idVenta=`) |
| GET | `/api/devoluciones/{id}` | — | Obtener una devolución por id |
| DELETE | `/api/devoluciones/{id}` | — | Anular una devolución (revierte el reingreso de stock) |

## Ejecución

```
./mvnw spring-boot:run
```

El servidor corre en **<http://localhost:8095>**.

Requiere que MySQL esté corriendo (XAMPP). La base de datos `db_ventas` se crea automáticamente (`createDatabaseIfNotExist=true`) y las tablas vía Hibernate (`ddl-auto=update`).

## Pruebas automatizadas

### Tests unitarios y de integración (JUnit + Mockito)

```
./mvnw test
```

El MS incluye dos dominios de prueba (ventas y devoluciones), cada uno con sus niveles unitario, web e integración:

- **`VentaServiceTest`** (unitario, Mockito): valida las reglas de negocio del service — cálculo de IVA (19%) y total, rechazo de descuento sobre el 50%, rechazo por stock insuficiente, actualización de descuento, anulación de venta con reversión de stock y sus restricciones. También cubre el flujo de **retiro web**: éxito confirmando la reserva (verifica que se llame a `confirmar-reserva` y **nunca** a `ajustar`), pedido ya retirado, pedido inexistente, pedido que no es de retiro en tienda, pedido no pagado y pedido sin detalles. Mockea las llamadas a los otros microservicios.
- **`DevolucionServiceTest`** (unitario, Mockito): valida las reglas de negocio de devoluciones — reingreso de stock, rechazo cuando el producto no pertenece a la venta, rechazo al devolver más de lo vendido (considerando devoluciones previas acumuladas, incluido el caso de límite exacto) y anulación con reversión del reingreso.
- **`VentaControllerTest`** (`@WebMvcTest`): valida la capa web de ventas aislada — códigos HTTP correctos (200/201/204/404/409) con el service mockeado, incluyendo el retiro exitoso (201) y el retiro de un pedido ya retirado (409).
- **`DevolucionControllerTest`** (`@WebMvcTest`): valida la capa web de devoluciones aislada — códigos HTTP correctos (200/201/204/404/409) con el service mockeado.
- **`VentaControllerIT`** (`@SpringBootTest` + `@ActiveProfiles("test")`): valida la cadena completa controller → service → base de datos, mockeando solo las llamadas a otros microservicios (`RestTemplate`). Verifica el cálculo de totales sobre una venta real, el rechazo de descuento sobre el tope y el 404 por venta inexistente.

## Estructura de requests y respuestas

### POST /api/ventas — Registrar venta presencial

```
// Request (venta presencial anónima: sin cliente ni pedido)
{
  "idSucursal": 1,
  "porcentajeDescuento": 0,
  "detalles": [
    {
      "idProducto": 1,
      "cantidad": 2,
      "precioUnitario": 45000
    }
  ]
}

// Response: 201 Created
{
  "idVenta": 1,
  "idPedido": null,
  "idCliente": null,
  "idSucursal": 1,
  "subtotalNeto": 90000,
  "porcentajeDescuento": 0,
  "iva": 17100,
  "total": 107100,
  "fecha": "2026-06-30T12:00:00",
  "detalles": [
    { "idDetalle": 1, "idProducto": 1, "cantidad": 2, "precioUnitario": 45000, "subtotal": 90000, "porcentajeDescuento": 0 }
  ]
}
```

**Reglas de negocio:**

- El descuento no puede superar el 50% sin autorización de gerente (409 Conflict)
- Cada producto debe existir en el catálogo (se valida contra MS Productos)
- **Verificación previa de disponibilidad:** antes de descontar stock, se comprueba que TODOS los productos tengan stock suficiente; si alguno no alcanza, se rechaza la venta completa (409 Conflict) sin tocar el stock — esto garantiza atomicidad
- El IVA es 19% calculado sobre el neto con descuento aplicado
- Al guardar, descuenta el stock de cada producto (ajuste negativo con idOperacion único para idempotencia)
- `idCliente` e `idPedido` son opcionales (null en venta presencial anónima)

### POST /api/ventas/retiro/{idPedido} — Registrar retiro de pedido web

```
// El idPedido viaja en la ruta. NO se envía cuerpo.
POST /api/ventas/retiro/5

// Response: 201 Created → misma estructura que la venta directa,
// con idPedido e idCliente tomados del pedido
```

**Reglas de negocio:**

- El `idPedido` se recibe como variable de ruta; **todos los datos de la venta se leen del pedido en MS Envíos**, no del cuerpo de la solicitud (sucursal = `idSucursalRetiro`, cliente, detalles, precios).
- El pedido debe existir (404 Not Found si MS Envíos no lo encuentra o llega sin detalles)
- No debe existir ya una venta asociada a ese pedido, es decir, el pedido no puede haber sido retirado antes (409 Conflict)
- El pedido debe estar en estado `PAGADO` (409 Conflict si no)
- El tipo de entrega del pedido debe ser `RETIRO_TIENDA` (409 Conflict si es despacho a domicilio u otro)
- **Confirma la reserva de stock** en MS Productos (`/api/inventario/confirmar-reserva`) en lugar de descontar stock: el pedido web ya había reservado las unidades al crearse
- Tras registrar la venta, marca el pedido como RETIRADO en MS Envíos (con degradación elegante si el MS no está disponible)
- Este endpoint cierra el flujo de compra web cuando el cliente eligió "retiro en tienda"

### GET /api/ventas — Listar ventas

```
GET /api/ventas
GET /api/ventas?idSucursal=1

Response: 200 OK → lista de ventas
Response: 204 No Content → si no hay resultados
```

### PUT /api/ventas/{id}/descuento — Actualizar descuento

```
PUT /api/ventas/1/descuento?porcentaje=20

// Response: 200 OK → venta con IVA y total recalculados
```

**Reglas de negocio:**

- El nuevo descuento no puede superar el 50% (409 Conflict)
- Recalcula el monto de descuento, el IVA y el total sobre el subtotal neto
- Propaga el nuevo porcentaje a cada detalle de la venta (para auditoría)

### DELETE /api/ventas/{id} — Anular venta

```
Response: 204 No Content
```

**Reglas:**

- Solo se pueden anular **ventas presenciales**. Si la venta proviene de un retiro web (tiene `idPedido`), se rechaza con 409 Conflict (`EstadoInvalidoException`), ya que su stock se maneja mediante la reserva del pedido.
- Al anular una venta presencial, reingresa al inventario el stock que se había descontado (ajuste positivo con idOperacion de prefijo `anulacion-venta-`) y luego elimina la venta.
- 404 si la venta no existe.

### POST /api/devoluciones — Procesar devolución

```
// Request
{
  "venta": { "idVenta": 1 },
  "detalles": [
    {
      "idProducto": 1,
      "cantidad": 1
    }
  ]
}

// Response: 201 Created
{
  "idDevolucion": 1,
  "venta": { "idVenta": 1 },
  "fecha": "2026-06-30T12:30:00",
  "detalles": [
    { "idDetalleDevolucion": 1, "idProducto": 1, "cantidad": 1 }
  ]
}
```

**Reglas de negocio:**

- La venta debe existir (404 si no)
- El producto debe pertenecer a la venta original (409 Conflict si no)
- No se puede devolver más de lo vendido, considerando devoluciones previas acumuladas (409 Conflict). Soporta devoluciones parciales en varias entregas
- Al procesar, reingresa el stock devuelto al MS Productos (ajuste positivo con idOperacion de prefijo `devolucion-`)

### DELETE /api/devoluciones/{id} — Anular devolución

```
Response: 204 No Content
```

**Regla:** Revierte el reingreso de stock que había generado la devolución (ajuste negativo con idOperacion de prefijo `anulacion-devolucion-`), luego elimina la devolución. 404 si no existe.

## Cálculo de una venta (ejemplo)

```
2 unidades × $45.000        = $90.000   (subtotalNeto)
descuento 0%                = $0
neto con descuento          = $90.000
IVA 19% sobre $90.000       = $17.100   (iva)
TOTAL                       = $107.100  (total)
```

Con un descuento del 20%, el cálculo sería: neto $90.000 − $18.000 = $72.000; IVA 19% = $13.680; total = $85.680.

## Manejo de errores

El MS usa un `GlobalExceptionHandler` que traduce las excepciones a códigos HTTP coherentes:

| Excepción | Código | Cuándo |
| --------- | ------ | ------ |
| `RecursoNoEncontradoException` | 404 Not Found | Venta, devolución, producto o pedido inexistente (o pedido sin detalles) |
| `DescuentoNoAutorizadoException` | 409 Conflict | Descuento superior al 50% sin autorización |
| `StockInsuficienteException` | 409 Conflict | No hay stock suficiente para la venta |
| `DevolucionInvalidaException` | 409 Conflict | Devolver más de lo vendido o producto ajeno a la venta |
| `EstadoInvalidoException` | 409 Conflict | Retiro de un pedido en estado inválido (no pagado, no es retiro en tienda, ya retirado) o intento de anular una venta de retiro web |
| `MethodArgumentNotValidException` | 400 Bad Request | Validación de campos fallida |
| `HttpMessageNotReadableException` | 400 Bad Request | JSON mal formado |
| `DataIntegrityViolationException` | 409 Conflict | El recurso ya existe o viola una restricción de la base de datos |
| `RestClientException` | 502 Bad Gateway | Error al comunicarse con otro microservicio |

## Idempotencia del stock

Todas las operaciones que ajustan stock envían un `idOperacion` único al MS Productos, con prefijos distintos según la operación:

- `venta-{id}-producto-{id}` — descuento por venta presencial
- `anulacion-venta-{id}-producto-{id}` — reingreso por anulación de venta
- `devolucion-{id}-producto-{id}` — reingreso por devolución
- `anulacion-devolucion-{id}-producto-{id}` — reverso de devolución

Esto garantiza que si una llamada se repite (por reintentos de red), el stock no se ajusta dos veces.

En el retiro web el stock no se vuelve a descontar: la venta **confirma la reserva** previamente hecha por el pedido mediante `/api/inventario/confirmar-reserva`.

## Configuración de base de datos

La aplicación usa MySQL. La base de datos `db_ventas` se crea automáticamente (`createDatabaseIfNotExist=true`). Las tablas se crean vía Hibernate (`ddl-auto=update`).

Credenciales por defecto en `application.properties`:

- Usuario: `root`
- Contraseña: *(vacía, como en XAMPP por defecto)*

URLs de los microservicios que consume (en `application.properties`):

```
ms.productos.url=http://localhost:8082/api/productos/
ms.inventario.ajuste.url=http://localhost:8082/api/inventario/ajustar
ms.inventario.disponibilidad.url=http://localhost:8082/api/inventario/disponibilidad
ms.inventario.confirmar.url=http://localhost:8082/api/inventario/confirmar-reserva
ms.clientes.url=http://localhost:8081/api/usuarios/
ms.pedidos.url=http://localhost:8091/api/v1/pedidos/
ms.pedidos.estado.url=http://localhost:8091/api/v1/pedidos/
ms.sucursales.url=http://localhost:8087/api/v1/sucursales/
```

## Swagger / OpenAPI

Documentación interactiva disponible en:

- Swagger UI: <http://localhost:8095/swagger-ui.html>
- API Docs (JSON): <http://localhost:8095/v3/api-docs>

Cada endpoint está documentado con su Historia de Usuario correspondiente para trazabilidad.