package com.cityfix.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    @DisplayName("removeSortCustomizer removes sort property from schemas and parameters")
    void removeSortCustomizer_removesSortPropertyAndParameters() {
        GlobalOpenApiCustomizer customizer = openApiConfig.removeSortCustomizer();

        OpenAPI openAPI = new OpenAPI();
        Components components = new Components();

        // Simulate Pageable schema
        Schema<Object> pageableSchema = new ObjectSchema();
        pageableSchema.setProperties(new HashMap<>());
        pageableSchema.getProperties().put("page", new IntegerSchema());
        pageableSchema.getProperties().put("size", new IntegerSchema());
        pageableSchema.getProperties().put("sort", new ArraySchema().items(new StringSchema()));
        components.addSchemas("Pageable", pageableSchema);
        openAPI.setComponents(components);

        // Simulate operation with sort parameter and pageable parameter
        Paths paths = new Paths();
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        List<Parameter> parameters = new ArrayList<>();

        Parameter sortParam = new QueryParameter().name("sort");
        parameters.add(sortParam);

        Parameter pageParam = new QueryParameter().name("page");
        parameters.add(pageParam);

        Parameter pageableParam = new QueryParameter().name("pageable");
        Schema<Object> paramSchema = new ObjectSchema();
        paramSchema.setProperties(new HashMap<>());
        paramSchema.getProperties().put("page", new IntegerSchema());
        paramSchema.getProperties().put("size", new IntegerSchema());
        paramSchema.getProperties().put("sort", new ArraySchema().items(new StringSchema()));
        pageableParam.setSchema(paramSchema);
        parameters.add(pageableParam);

        operation.setParameters(parameters);
        pathItem.setGet(operation);
        paths.addPathItem("/api/v1/test", pathItem);
        openAPI.setPaths(paths);

        // Execute customizer
        customizer.customise(openAPI);

        // Assert schema sort property is removed
        assertThat(openAPI.getComponents().getSchemas().get("Pageable").getProperties())
                .containsKey("page")
                .containsKey("size")
                .doesNotContainKey("sort");

        // Assert operation parameters: sort query param is removed
        assertThat(operation.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("page", "pageable");

        // Assert inline pageable parameter schema has sort removed
        assertThat(pageableParam.getSchema().getProperties())
                .containsKey("page")
                .containsKey("size")
                .doesNotContainKey("sort");
    }

    @Test
    @DisplayName("removeSortOperationCustomizer removes sort parameter and inlined sort property")
    void removeSortOperationCustomizer_removesSort() {
        OperationCustomizer operationCustomizer = openApiConfig.removeSortOperationCustomizer();

        Operation operation = new Operation();
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new QueryParameter().name("sort"));
        parameters.add(new QueryParameter().name("page"));

        Parameter pageableParam = new QueryParameter().name("pageable");
        Schema<Object> paramSchema = new ObjectSchema();
        paramSchema.setProperties(new HashMap<>());
        paramSchema.getProperties().put("sort", new ArraySchema().items(new StringSchema()));
        pageableParam.setSchema(paramSchema);
        parameters.add(pageableParam);

        operation.setParameters(parameters);

        operationCustomizer.customize(operation, null);

        assertThat(operation.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("page", "pageable");

        assertThat(pageableParam.getSchema().getProperties())
                .doesNotContainKey("sort");
    }
}
