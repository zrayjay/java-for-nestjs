package dev.javafornestjs.capstone;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiContractConfiguration {
    @Bean
    OpenAPI capstoneContract() {
        return new OpenAPI()
                .info(new Info().title("Capstone Order Management API").version("1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer capstoneOperations() {
        return contract -> {
            contract.getComponents().addSchemas("ApiError", new ObjectSchema()
                    .addProperty("code", new StringSchema())
                    .addProperty("message", new StringSchema())
                    .addProperty("requestId", new StringSchema())
                    .required(List.of("code", "message", "requestId")));
            contract.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/refresh")) {
                    operation.setSecurity(List.of());
                }
                operation.getResponses().addApiResponse("default", new ApiResponse().description("API error")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
            }));
            contract.getComponents().getSchemas().forEach((name, schema) -> {
                if (name.startsWith("dev.javafornestjs.capstone.system.")
                        || name.startsWith("dev.javafornestjs.capstone.orders.")) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet().stream().sorted().toList()));
                }
                if (name.contains("MenuService.") || name.endsWith("Controller.PatchMenu")
                        || name.endsWith("Controller.ChangeMenu")) {
                    nullable(schema, "parentId", "routeKey", "permission");
                } else if (name.endsWith("OrdersService.History")) {
                    nullable(schema, "fromStatus");
                }
            });
        };
    }

    private static void nullable(Schema<?> schema, String... names) {
        for (String name : names) {
            var property = schema.getProperties().get(name);
            if (property != null) {
                property.setTypes(new LinkedHashSet<>(List.of("string", "null")));
            }
        }
    }
}
