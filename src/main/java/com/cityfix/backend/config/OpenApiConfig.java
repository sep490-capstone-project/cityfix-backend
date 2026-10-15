package com.cityfix.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI cityFixOpenAPI() {
        return new OpenAPI()
                .info(buildApiInfo())
                .servers(buildServers())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, buildBearerSecurityScheme()));
    }

    private Info buildApiInfo() {
        return new Info()
                .title("CityFix API")
                .description("REST API documentation for the CityFix service platform")
                .version("v1.0.0")
                .contact(new Contact()
                        .name("CityFix")
                        .email("support@cityfix.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    private List<Server> buildServers() {
        return List.of(
                new Server()
                        .url("/")
                        .description("Current Server URL"),
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local Development Server")
        );
    }

    private SecurityScheme buildBearerSecurityScheme() {
        return new SecurityScheme()
                .name(BEARER_AUTH_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Provide a valid JWT token in the Authorization header");
    }

    @Bean
    public GlobalOpenApiCustomizer removeSortCustomizer() {
        return openApi -> {
            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                openApi.getComponents().getSchemas().values().forEach(this::cleanSchema);
            }
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(pathItem -> {
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getParameters() != null) {
                            operation.getParameters().removeIf(param -> "sort".equalsIgnoreCase(param.getName()));
                            operation.getParameters().forEach(param -> {
                                if (param.getSchema() != null) {
                                    cleanSchema(param.getSchema());
                                }
                            });
                        }
                    });
                });
            }
        };
    }

    @Bean
    public OperationCustomizer removeSortOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().removeIf(param -> "sort".equalsIgnoreCase(param.getName()));
                operation.getParameters().forEach(param -> {
                    if (param.getSchema() != null) {
                        cleanSchema(param.getSchema());
                    }
                });
            }
            return operation;
        };
    }

    private void cleanSchema(Schema<?> schema) {
        if (schema == null) return;
        if (schema.getProperties() != null) {
            schema.getProperties().remove("sort");
        }
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(this::cleanSchema);
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(this::cleanSchema);
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(this::cleanSchema);
        }
    }
}
