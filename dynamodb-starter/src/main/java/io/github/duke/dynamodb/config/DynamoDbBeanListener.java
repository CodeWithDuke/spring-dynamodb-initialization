package io.github.duke.dynamodb.config;

import io.awspring.cloud.dynamodb.DefaultDynamoDbTableNameResolver;
import io.github.duke.dynamodb.exception.EntityNotFoundException;
import io.github.duke.dynamodb.utils.DynamoDbStarterUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import io.github.duke.dynamodb.annotation.DynamoDbDocument;

import java.lang.reflect.Method;
import java.util.*;

/**
 * An {@link ApplicationListener} implementation for DynamoDB bean initialization.
 * <p>
 * This listener scans the classpath for beans annotated with {@link DynamoDbBean},
 * and ensures that corresponding DynamoDB tables are created based on the provided configurations.
 */
@Slf4j
@Component
public class DynamoDbBeanListener implements ApplicationListener<ContextRefreshedEvent> {

    private static final String CLASS_NOT_FOUND_MESSAGE = "Invalid class name";

    private final DynamoDbClient dynamoDbClient;
    private final DefaultDynamoDbTableNameResolver prefixedTableNameResolver;
    private final String tablePrefix;
    private final String packageName;
    private final BillingMode billingMode;
    private final long writeCapacity;
    private final long readCapacity;

    /**
     * Constructs a DynamoDbBeanListener with the necessary dependencies.
     *
     * @param dynamoDbClient            The DynamoDB client.
     * @param prefixedTableNameResolver The table name resolver.
     * @param tablePrefix               The table prefix.
     * @param packageName               The package name to scan for DynamoDB beans.
     * @param billingMode               The billing mode for DynamoDB tables.
     * @param writeCapacity             The provisioned write capacity for DynamoDB tables.
     * @param readCapacity              The provisioned read capacity for DynamoDB tables.
     */
    @Autowired
    public DynamoDbBeanListener(DynamoDbClient dynamoDbClient, DefaultDynamoDbTableNameResolver prefixedTableNameResolver,
                                @Value("${dynamodb.starter.table.prefix}") String tablePrefix,
                                @Value("${dynamodb.starter.package.scan:''}") String packageName,
                                @Value("${dynamodb.starter.billing.mode}") BillingMode billingMode,
                                @Value("${dynamodb.starter.throughput.writeCapacity:10}") long writeCapacity,
                                @Value("${dynamodb.starter.throughput.readCapacity:10}") long readCapacity) {
        this.dynamoDbClient = dynamoDbClient;
        this.prefixedTableNameResolver = prefixedTableNameResolver;
        this.tablePrefix = tablePrefix;
        this.packageName = packageName;
        this.billingMode = billingMode;
        this.writeCapacity = writeCapacity;
        this.readCapacity = readCapacity;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        scanForDynamoDbBeans();
    }

    /**
     * {@inheritDoc}
     */
    private void scanForDynamoDbBeans() {
        ClassPathScanningCandidateComponentProvider scanner = createClassPathScanner();
        ListTablesResponse tableList = listExistingTables();
        processBeans(scanner, tableList);
    }

    /**
     * Creates an instance of {@link ClassPathScanningCandidateComponentProvider} for DynamoDB beans.
     *
     * @return The configured scanner.
     */
    private ClassPathScanningCandidateComponentProvider createClassPathScanner() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(DynamoDbBean.class));
        return scanner;
    }

    /**
     * Lists existing DynamoDB tables.
     *
     * @return The response containing the list of tables.
     */
    private ListTablesResponse listExistingTables() {
        ListTablesRequest tablesRequest = ListTablesRequest.builder()
                .exclusiveStartTableName(tablePrefix)
                .build();
        return dynamoDbClient.listTables(tablesRequest);
    }

    /**
     * Processes DynamoDB beans, checks for existing tables, and creates new ones if needed.
     *
     * @param scanner   The component scanner.
     * @param tableList The list of existing tables.
     */
    private void processBeans(ClassPathScanningCandidateComponentProvider scanner, ListTablesResponse tableList) {
        for (BeanDefinition bd : scanner.findCandidateComponents(packageName)) {
            processBean(bd, tableList);
        }
    }

    /**
     * Processes an individual DynamoDB bean, checking for the existence of its table,
     * and creating a new table if needed.
     *
     * @param bd        The BeanDefinition of the DynamoDB bean.
     * @param tableList The list of existing tables.
     */
    private void processBean(BeanDefinition bd, ListTablesResponse tableList) {
        Class<?> entity = resolveEntityClass(bd);
        if (entity.isAnnotationPresent(DynamoDbDocument.class)) {
            return;
        }

        String tableName = prefixedTableNameResolver.resolve(entity);

        if (tableList.tableNames().contains(tableName)) {
            log.debug("Table {} already exists. Skipping.", tableName);
            return;
        }

        String[] localIndexName = DynamoDbStarterUtils.getLocalIndexName(entity);

        List<Method> keys = DynamoDbStarterUtils.getAnnotatedMethods(entity);

        List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        List<KeySchemaElement> tableKeySchema = new ArrayList<>();
        HashMap<String, List<KeySchemaElement>> globalSecondaryIndexKeySchema = new HashMap<>();
        HashMap<String, List<KeySchemaElement>> localSecondaryIndexKeySchema = new HashMap<>();

        attributeAndKeysResolver(attributeDefinitions, tableKeySchema, keys, globalSecondaryIndexKeySchema, localSecondaryIndexKeySchema, localIndexName);

        List<GlobalSecondaryIndex> globalSecondaryIndices = globalSecondaryIndicesResolver(globalSecondaryIndexKeySchema);
        List<LocalSecondaryIndex> localSecondaryIndices = localSecondaryIndicesResolver(localSecondaryIndexKeySchema);

        tableKeySchema.sort(Comparator.comparing(t -> t.keyType().toString()));
        CreateTableRequest createTableRequest = tableRequestResolver(tableName, attributeDefinitions, tableKeySchema, globalSecondaryIndices, localSecondaryIndices);

        try {
            dynamoDbClient.createTable(createTableRequest);
            log.debug("Table {} installation successful.", tableName);
        } catch (ResourceInUseException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * Resolves the entity class from the provided {@link BeanDefinition}.
     *
     * @param bd The BeanDefinition of the DynamoDB bean.
     * @return The resolved entity class.
     * @throws EntityNotFoundException If the class is not found.
     */
    private Class<?> resolveEntityClass(BeanDefinition bd) {
        try {
            return Class.forName(bd.getBeanClassName());
        } catch (ClassNotFoundException e) {
            log.error(CLASS_NOT_FOUND_MESSAGE, e);
            throw new EntityNotFoundException(CLASS_NOT_FOUND_MESSAGE);
        }
    }

    /**
     * Resolves the {@link CreateTableRequest} based on the provided parameters.
     *
     * @param tableName              The name of the DynamoDB table.
     * @param attributeDefinitions   The list of attribute definitions.
     * @param keySchemas             The list of key schema elements.
     * @param globalSecondaryIndices The list of global secondary indexes.
     * @param localSecondaryIndices  The list of local secondary indexes.
     * @return The resolved CreateTableRequest.
     */
    private CreateTableRequest tableRequestResolver(String tableName, List<AttributeDefinition> attributeDefinitions,
                                                    List<KeySchemaElement> keySchemas,
                                                    List<GlobalSecondaryIndex> globalSecondaryIndices,
                                                    List<LocalSecondaryIndex> localSecondaryIndices) {
        CreateTableRequest.Builder createTableRequest = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(attributeDefinitions)
                .keySchema(keySchemas);
        if (!CollectionUtils.isEmpty(globalSecondaryIndices)) {
            createTableRequest.globalSecondaryIndexes(globalSecondaryIndices);
        }
        if (!CollectionUtils.isEmpty(localSecondaryIndices)) {
            createTableRequest.localSecondaryIndexes(localSecondaryIndices);
        }

        if (billingMode.equals(BillingMode.PROVISIONED)) {
            createTableRequest
                    .provisionedThroughput(
                            ProvisionedThroughput.builder()
                                    .readCapacityUnits(readCapacity).writeCapacityUnits(writeCapacity).build())
                    .build();
        } else {
            createTableRequest
                    .billingMode(BillingMode.PAY_PER_REQUEST);
        }
        return createTableRequest.build();
    }

    /**
     * Resolves attributes and keys for the DynamoDB table.
     *
     * @param attributeDefinitions      The list of attribute definitions.
     * @param tableKeySchema            The list of key schema elements for the main table.
     * @param keys                      The list of annotated methods representing keys.
     * @param globalSecondaryIndexKeySchema The map of global secondary index key schemas.
     * @param localSecondaryIndexKeySchema  The map of local secondary index key schemas.
     * @param localIndexName            The array of local index names.
     */
    private void attributeAndKeysResolver(List<AttributeDefinition> attributeDefinitions, List<KeySchemaElement> tableKeySchema,
                                          List<Method> keys, HashMap<String, List<KeySchemaElement>> globalSecondaryIndexKeySchema,
                                          HashMap<String, List<KeySchemaElement>> localSecondaryIndexKeySchema, String[] localIndexName) {
        for (Method key : keys) {
            attributeDefinitions.add(AttributeDefinition.builder()
                    .attributeName(DynamoDbStarterUtils.getFieldName(key.getName()))
                    .attributeType(DynamoDbStarterUtils.getScalarAttributeType(key.getReturnType()))
                    .build());
            tableKeySchemaResolver(key, tableKeySchema);
            secondaryIndexKeySchemaResolver(key, globalSecondaryIndexKeySchema, localSecondaryIndexKeySchema, localIndexName);
        }
    }

    /**
     * Resolves the key schema for the main table based on the provided method.
     *
     * @param key           The annotated method representing a key.
     * @param tableKeySchema The list of key schema elements for the main table.
     */
    private void tableKeySchemaResolver(Method key, List<KeySchemaElement> tableKeySchema) {
        if (key.isAnnotationPresent(DynamoDbPartitionKey.class)) {
            tableKeySchema.add(KeySchemaElement.builder().
                    attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.HASH).build());
        } else if (key.isAnnotationPresent(DynamoDbSortKey.class)) {
            tableKeySchema.add(KeySchemaElement.builder().
                    attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.RANGE).build());
        }
    }

    /**
     * Resolves the key schema for secondary indexes based on the provided method.
     *
     * @param key                        The annotated method representing a key.
     * @param globalSecondaryIndexKeySchema The map of global secondary index key schemas.
     * @param localSecondaryIndexKeySchema  The map of local secondary index key schemas.
     * @param localIndexNames            The array of local index names.
     */
    private void secondaryIndexKeySchemaResolver(Method key, HashMap<String, List<KeySchemaElement>> globalSecondaryIndexKeySchema,
                                                 HashMap<String, List<KeySchemaElement>> localSecondaryIndexKeySchema, String[] localIndexNames) {
        if (key.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)) {
            DynamoDbSecondaryPartitionKey dynamoDbSecondaryPartitionKey = key.getAnnotation(DynamoDbSecondaryPartitionKey.class);
            String[] indexNames = dynamoDbSecondaryPartitionKey.indexNames();
            for (String index : indexNames) {
                List<KeySchemaElement> elements;
                if (Arrays.asList(localIndexNames).contains(index)) {
                    elements = localSecondaryIndexKeySchema.getOrDefault(index, new ArrayList<>());
                    elements.add(KeySchemaElement.builder().
                            attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.HASH).build());
                    localSecondaryIndexKeySchema.put(index, elements);
                } else {
                    elements = globalSecondaryIndexKeySchema.getOrDefault(index, new ArrayList<>());
                    elements.add(KeySchemaElement.builder().
                            attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.HASH).build());
                    globalSecondaryIndexKeySchema.put(index, elements);
                }
            }
        }
        if (key.isAnnotationPresent(DynamoDbSecondarySortKey.class)) {
            DynamoDbSecondarySortKey dynamoDbSecondarySortKey = key.getAnnotation(DynamoDbSecondarySortKey.class);
            String[] indexNames = dynamoDbSecondarySortKey.indexNames();
            for (String index : indexNames) {
                List<KeySchemaElement> elements;
                if (Arrays.asList(localIndexNames).contains(index)) {
                    elements = localSecondaryIndexKeySchema.getOrDefault(index, new ArrayList<>());
                    elements.add(KeySchemaElement.builder().
                            attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.RANGE).build());
                    localSecondaryIndexKeySchema.put(index, elements);
                } else {
                    elements = globalSecondaryIndexKeySchema.getOrDefault(index, new ArrayList<>());
                    elements.add(KeySchemaElement.builder().
                            attributeName(DynamoDbStarterUtils.getFieldName(key.getName())).keyType(KeyType.RANGE).build());
                    globalSecondaryIndexKeySchema.put(index, elements);

                }
            }
        }
    }

    /**
     * Resolves the list of global secondary indexes based on the provided index key schema.
     *
     * @param indexKeySchema The map of global secondary index key schemas.
     * @return The list of resolved global secondary indexes.
     */
    private List<GlobalSecondaryIndex> globalSecondaryIndicesResolver(HashMap<String, List<KeySchemaElement>> indexKeySchema) {
        List<GlobalSecondaryIndex> globalSecondaryIndices = new ArrayList<>();
        indexKeySchema.forEach((indexName, indexSchemaList) -> {
            indexSchemaList.sort(Comparator.comparing(t -> t.keyType().toString()));
            GlobalSecondaryIndex glsIndex = GlobalSecondaryIndex.builder().indexName(indexName)
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build()).keySchema(indexSchemaList)
                    .build();
            globalSecondaryIndices.add(glsIndex);
        });
        return globalSecondaryIndices;
    }

    /**
     * Resolves the list of local secondary indexes based on the provided index key schema.
     *
     * @param indexKeySchema The map of local secondary index key schemas.
     * @return The list of resolved local secondary indexes.
     */
    private List<LocalSecondaryIndex> localSecondaryIndicesResolver(HashMap<String, List<KeySchemaElement>> indexKeySchema) {
        List<LocalSecondaryIndex> localSecondaryIndices = new ArrayList<>();
        indexKeySchema.forEach((indexName, indexSchemaList) -> {
            indexSchemaList.sort(Comparator.comparing(t -> t.keyType().toString()));
            LocalSecondaryIndex glsIndex = LocalSecondaryIndex.builder().indexName(indexName)
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build()).keySchema(indexSchemaList)
                    .build();
            localSecondaryIndices.add(glsIndex);
        });
        return localSecondaryIndices;
    }
}
