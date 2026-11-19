package dev.javafornestjs.capstone;

import com.fasterxml.jackson.annotation.JsonSetter;
import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AuthorizationService;
import dev.javafornestjs.capstone.system.MenuService;
import dev.javafornestjs.capstone.system.MenuService.Field;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static dev.javafornestjs.capstone.ResourceIds.parseId;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

@RestController
@RequestMapping("/api/v1")
class AuthorizationController {
    private final AuthorizationService roles;
    private final MenuService menus;

    AuthorizationController(AuthorizationService roles, MenuService menus) {
        this.roles = roles;
        this.menus = menus;
    }

    @GetMapping("/roles")
    AuthorizationService.RolePage roles(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return roles.roles(SessionIdentity.from(jwt), page, size);
    }

    @PostMapping("/roles")
    ResponseEntity<AuthorizationService.Role> createRole(@AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateRole request) {
        var role = roles.create(SessionIdentity.from(jwt), request.code(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/roles/" + role.id())).body(role);
    }

    @PatchMapping("/roles/{id}")
    AuthorizationService.Role renameRole(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody RenameRole request) {
        return roles.rename(SessionIdentity.from(jwt), parseId(id), request.name());
    }

    @PutMapping("/roles/{id}/grants")
    ResponseEntity<Void> replaceGrants(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody ReplaceGrants request) {
        roles.replaceGrants(SessionIdentity.from(jwt), parseId(id), request.grants());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/roles")
    ResponseEntity<Void> replaceRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody ReplaceRoles request) {
        var roleIds = request.roleIds();
        roles.replaceRoles(SessionIdentity.from(jwt), parseId(id),
                roleIds == null ? null : roleIds.stream().map(ResourceIds::parseId).toList());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/platform-admin")
    ResponseEntity<Void> platformAdministrator(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody AdministratorToggle request) {
        if (request.enabled() == null) {
            throw new RequestFailure(VALIDATION_ERROR, "Provide the administrator status");
        }
        roles.changeAdministrator(SessionIdentity.from(jwt), parseId(id), request.enabled());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/menus")
    List<MenuService.Node> currentMenus(@AuthenticationPrincipal Jwt jwt) {
        return menus.visible(SessionIdentity.from(jwt));
    }

    @GetMapping("/menus")
    List<MenuService.Menu> menus(@AuthenticationPrincipal Jwt jwt) {
        return menus.all(SessionIdentity.from(jwt));
    }

    @PostMapping("/menus")
    ResponseEntity<MenuService.Menu> createMenu(@AuthenticationPrincipal Jwt jwt, @RequestBody ChangeMenu request) {
        var menu = menus.create(SessionIdentity.from(jwt), request.toChange());
        return ResponseEntity.created(URI.create("/api/v1/menus/" + menu.id())).body(menu);
    }

    @PatchMapping("/menus/{id}")
    MenuService.Menu updateMenu(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody PatchMenu request) {
        return menus.update(SessionIdentity.from(jwt), parseId(id), request.toPatch());
    }

    @DeleteMapping("/menus/{id}")
    ResponseEntity<Void> deleteMenu(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        menus.delete(SessionIdentity.from(jwt), parseId(id));
        return ResponseEntity.noContent().build();
    }

    record CreateRole(String code, String name) {
    }

    record RenameRole(String name) {
    }

    record ReplaceGrants(List<AuthorizationService.Grant> grants) {
    }

    record ReplaceRoles(List<String> roleIds) {
    }

    record AdministratorToggle(Boolean enabled) {
    }

    record ChangeMenu(String parentId, String label, String routeKey, String permission, Integer sortOrder) {
        MenuService.MenuChange toChange() {
            return new MenuService.MenuChange(parentId == null ? null : parseId(parentId), label, routeKey,
                    permission, sortOrder);
        }
    }

    static final class PatchMenu {
        private Field<Long> parentId = Field.omitted();
        private Field<String> label = Field.omitted();
        private Field<String> routeKey = Field.omitted();
        private Field<String> permission = Field.omitted();
        private Field<Integer> sortOrder = Field.omitted();

        @JsonSetter
        public void setParentId(String value) {
            parentId = Field.supplied(value == null ? null : parseId(value));
        }

        @JsonSetter
        public void setLabel(String value) {
            label = Field.supplied(value);
        }

        @JsonSetter
        public void setRouteKey(String value) {
            routeKey = Field.supplied(value);
        }

        @JsonSetter
        public void setPermission(String value) {
            permission = Field.supplied(value);
        }

        @JsonSetter
        public void setSortOrder(Integer value) {
            sortOrder = Field.supplied(value);
        }

        MenuService.MenuPatch toPatch() {
            return new MenuService.MenuPatch(parentId, label, routeKey, permission, sortOrder);
        }
    }
}
