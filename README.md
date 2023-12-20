# DynamoDB Starter for Spring

Welcome to DynamoDB Starter for Spring! This library simplifies the integration of DynamoDB with your Spring application.

## Getting Started

### 1. Annotate Your Entities

Make sure to annotate your entities and entity objects with `@DynamoDbBean`. For child objects or arrays, include `@DynamoDbDocument`.
Dynamodb Entity
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
@LocalDynamoSecondaryInfo(localIndexNames = "index1")
public class AccidentInformation {
    private String id;
    private String phoneNumber;
    private Integer time;
    private String latList;
    private String lonList;
    private VehicleNumber vehicleNumber;
    private String vehicleModel;
    private String address;
    private Integer accidentType;
    private String createdAt;
    private Boolean deleteFlag;
    private String receiveType;
    private String client;
    private String line;
    private String receivedNo;
    private String receivedDate;


    @DynamoDbPartitionKey
    @DynamoDbAttribute(value = "id")
    @DynamoDbSecondaryPartitionKey(indexNames = "index1")
    public String getId() {
        return id;
    }

    @DynamoDbSortKey
    public Integer getAccidentType() {
        return accidentType;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "client-receivedDate")
    public String getClient() {
        return client;
    }

    @DynamoDbSecondarySortKey(indexNames = {"client-receivedDate", "line-receivedDate"})
    public String getReceivedDate() {
        return receivedDate;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "line-receivedDate")
    public String getLine() {
        return line;
    }

    @DynamoDbSecondarySortKey(indexNames = "index1")
    public String getCreatedAt() {
        return createdAt;
    }
```
Dynamodb Child Object
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
@DynamoDbDocument
public class VehicleNumber {
    private String vehicleNumberIssuingOffice;
    private String vehicleNumberClassCode;
    private String vehicleNumberKana;
    private String vehicleNumberFourDigit;
}
```
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

# Set the package name
dynamodb.starter.package.scan=your.package.name