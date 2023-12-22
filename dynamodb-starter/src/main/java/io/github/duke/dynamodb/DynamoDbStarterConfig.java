package io.github.duke.dynamodb;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for DynamoDB Starter.
 * <p>
 * This class is annotated with {@link Configuration} to indicate that it contains
 * bean definitions for the Spring application context. It is also annotated with
 * {@link ComponentScan}, which triggers the scanning of components (e.g., Spring beans)
 * within the same package and its subpackages.
 * <p>
 * DynamoDB Starter provides configuration and component scanning for DynamoDB-related
 * functionality in a Spring application.
 *
 * @see Configuration
 * @see ComponentScan
 */
@Configuration
@ComponentScan
public class DynamoDbStarterConfig {
}
