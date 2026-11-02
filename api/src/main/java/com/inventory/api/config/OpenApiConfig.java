package com.inventory.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "InventoryApp API", version = "v1", description = "Items, stock movements, CSV import and low-stock alerts"))
public class OpenApiConfig {
}
