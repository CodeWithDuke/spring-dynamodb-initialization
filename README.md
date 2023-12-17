# DynamoDB Starter for Spring

Welcome to DynamoDB Starter for Spring! This library simplifies the integration of DynamoDB with your Spring application.

## Getting Started

### 1. Annotate Your Entities

Make sure to annotate your entities and entity objects with `@DynamoDbBean`. For child objects or arrays, include `@DynamoDbDocument`.

### 2. Add Configuration Properties

In your application properties file, include the following properties to configure DynamoDB:

```properties
# Set a prefix for your DynamoDB table name (optional)
dynamodb.starter.table.prefix=local_

# Choose your billing mode (options: PROVISIONED, PAY_PER_REQUEST)
dynamodb.starter.billing.mode=PAY_PER_REQUEST

# Set the write capacity for your DynamoDB table
dynamodb.starter.throughput.writeCapacity=10

# Set the read capacity for your DynamoDB table
dynamodb.starter.throughput.readCapacity=10
