package dev.javafornestjs.capstone;

import dev.javafornestjs.capstone.orders.OrdersService;
import dev.javafornestjs.capstone.orders.OrdersService.Action;
import dev.javafornestjs.capstone.security.SessionIdentity;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static dev.javafornestjs.capstone.ResourceIds.parseId;

@RestController
@RequestMapping("/api/v1/orders")
class OrdersController {
    private final OrdersService orders;

    OrdersController(OrdersService orders) {
        this.orders = orders;
    }

    @GetMapping
    OrdersService.Page orders(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return orders.list(SessionIdentity.from(jwt), status, page, size);
    }

    @GetMapping("/{id}")
    OrdersService.Order detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return orders.detail(SessionIdentity.from(jwt), parseId(id));
    }

    @GetMapping("/{id}/history")
    List<OrdersService.History> history(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return orders.history(SessionIdentity.from(jwt), parseId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<OrdersService.Order> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateOrder request) {
        var order = orders.create(SessionIdentity.from(jwt), request.customerName(), request.totalAmountCents());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
    }

    @PatchMapping("/{id}")
    OrdersService.Order edit(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody EditOrder data) {
        return orders.edit(SessionIdentity.from(jwt), parseId(id), data.version(), data.customerName(),
                data.totalAmountCents());
    }

    @PostMapping("/{id}/submit")
    OrdersService.Order submit(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody Version data) {
        return orders.transition(SessionIdentity.from(jwt), parseId(id), data.version(), Action.SUBMIT);
    }

    @PostMapping("/{id}/approve")
    OrdersService.Order approve(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody Version data) {
        return orders.transition(SessionIdentity.from(jwt), parseId(id), data.version(), Action.APPROVE);
    }

    @PostMapping("/{id}/cancel")
    OrdersService.Order cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody Version data) {
        return orders.transition(SessionIdentity.from(jwt), parseId(id), data.version(), Action.CANCEL);
    }

    record CreateOrder(String customerName, Integer totalAmountCents) {
    }

    record EditOrder(String customerName, Integer totalAmountCents, Integer version) {
    }

    record Version(Integer version) {
    }
}
