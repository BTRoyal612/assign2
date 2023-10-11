# Project Overview

Welcome to the **Distributed Content Aggregation System**. This system is a culmination of modern software design principles aimed at creating an efficient, scalable, and modular content management system. At its core, the project brings together multiple components that interact seamlessly to handle, aggregate, and serve content.

---

## Key Features

- **Distributed Architecture**: This system is designed to scale. Multiple components can be added or removed without disrupting the entire service, providing high availability and fault tolerance.

- **Modular Design**: With clear separation between different functionalities such as aggregation, content handling, client requests, and networking, the system ensures that adding new features or modifying existing ones remains hassle-free.

- **Lamport Clock Implementation**: Ensures synchronization and ordering of events across the distributed system. It's a crucial feature for maintaining data integrity and order in a distributed setup.

- **Efficient Content Serving**: With the load balancer in place, content requests are efficiently distributed amongst available servers, ensuring fast response times and load distribution.

- **JUnit Testing**: A comprehensive set of unit and integration tests ensures that the system remains bug-free and performs optimally under various conditions.

---

## Directory Structure

The codebase is structured to ensure clarity and ease of access. Here's a rundown of the primary directory organization:

```
- src/
  - main/
    - aggregation/
      - AggregationServer.java: Manages and orchestrates content aggregation operations.
      - DataStoreService.java: Provides services for storing and retrieving aggregated data.
      - LoadBalancer.java: Distributes incoming traffic to available aggregation servers.
    - content/
      - ContentServer.java: Responsible for managing and serving content.
      - input_v1.txt, input_v2.txt, input_v3.txt: Various versions of input data files.
    - client/
      - GETClient.java: Represents a client that fetches content from the content server.
    - common/
      - JsonHandler.java: Utility for handling JSON data parsing and generation.
      - LamportClock.java: Implements Lamport's logical clock for event synchronization.
      - WeatherData.java: Data model representing weather-related content.
    - network/
      - NetworkHandler.java: Abstracts network communication functionalities.
      - SocketNetworkHandler.java: Implementation of network handler using sockets.
  - test/
    - aggregation/
      - AggregationServerTest.java: Tests functionalities of the AggregationServer.
      - DataStoreServiceTest.java: Ensures data storage and retrieval works as expected.
      - LoadBalancerTest.java: Validates the load distribution logic.
    - content/
      - ContentServerTest.java: Validates content serving logic and response integrity.
    - client/
      - GETClientTest.java: Tests the client's ability to request and receive content.
    - common/
      - JsonHandlerTest.java: Checks JSON parsing and generation functionalities.
    - network/
      - StubSocketNetworkHandler.java: Mock implementation of the socket handler for testing purposes.
```

This tree structure provides a clear and comprehensive breakdown of the project, ensuring developers can quickly identify and locate specific components.

---

## File Management and Data Storage

### Overview

In the course of this project, I emphasized not only the functional efficiency but also the organization and management of data. I employed JSON-based storage mechanisms for structured and easy retrieval of information. This section elaborates on how I store weather data and track connections from the Content Server (CS).

### Weather Data Storage: `dataStore.json`

The `dataStore.json` located in the `data` folder within `src` serves as our primary repository for weather data. It is structured to ensure quick access, updates, and searches:

- **Key**: Each weather data entry is keyed by the `stationID`.
- **Value**: The value corresponding to each key is a JSON object containing all pertinent information about the weather station.

Example:
```json
{
    "IDS60901": {
        "stationName": "Sample Station",
        "temperature": 22,
        "humidity": 75,
        "lastUpdated": "2023-10-11T14:48:00Z"
    }
}
```
This structure ensures that data retrieval based on a stationID is fast, making the system efficient even with large volumes of data.

### Connection Tracking: `timestampStore.json`

To ensure robustness and reliability, I also track the connections from the Content Server. The `timestampStore.json` in the data folder plays a crucial role in this.

- **Key**: Each entry is keyed by the unique identifier for the Content Server.
- **Value**: The value holds the timestamp of the last connection or data update received from that Content Server.

Example:
```json
{
    "CS_001": "2023-10-11T14:48:00Z",
    "CS_002": "2023-10-11T13:45:30Z"
}
```

This approach ensures that I can quickly determine the last time I received data from a particular Content Server, aiding in identifying connection issues or dormant servers.

---

## Lamport Implementation

The Lamport clock is a logical clock system that ensures a total ordering of events in a distributed system. In essence, it assists in defining the order of events as "happened before" in scenarios where physical time isn't a reliable metric. Our project employs the Lamport clock to synchronize events across different modules.

### Individual and Shared Clocks

Every aggregation server overseen by the load balancer maintains its own instance of a Lamport clock. Additionally, there's a shared Lamport clock for overarching synchronization:
- Individual Lamport Clock: Represents the local view of time for an aggregation server.
- Shared Lamport Clock: Reflects a global view of time across the system, facilitating synchronization between individual aggregation servers.

### Event Synchronization

Upon the receipt of any request, the aggregation server instantly communicates its current Lamport clock value to the sender. This preemptive sharing aims to ensure the order of events is consistent across the distributed system.

### System Workflow

To get a clearer understanding, let's walk through a typical request-response lifecycle in the system:

1. **Initialization**:
    - Client or Content Server initiates and dispatches a socket for communication. Given the preparatory nature of this step, it doesn't influence the Lamport Clock.

2. **Load Balancer Distribution**:
    - The Load Balancer acknowledges the socket and routes it to an Aggregation Server (AS) based on a round-robin distribution strategy. Again, this step doesn't affect the Lamport Clock value.

3. **Lamport Clock Sharing**:
    - The designated AS sends its current Lamport clock value to the address from which the socket originated. This step is passive and doesn't increment the Lamport Clock.

4. **Client Request**:
    - The Client or Content Server issues a request. This action increments their local Lamport Clock.

5. **AS Request Acknowledgment**:
    - The AS receives the request and updates its local Lamport Clock accordingly.

6. **Synchronization with Shared Clock**:
    - The AS synchronizes its local Lamport Clock with the shared clock, updating the shared clock in the process.

7. **Response Creation and Dispatch**:
    - The AS formulates a response and sends it off, incrementing its local Lamport Clock as it does.

8. **Global Synchronization**:
    - The AS synchronizes its local clock with the shared Lamport Clock once more.

9. **Client Response Receipt**:
    - The Client or Content Server receives the AS's response. This finalizes the request-response cycle and prompts an increment of their local Lamport Clock.

The meticulous use of the Lamport Clock ensures that the system maintains a consistent understanding of the order of events, which is critical for the accuracy and reliability of any distributed system.

---

## Testing Methodologies

Ensuring the robustness, reliability, and correctness of our project is of paramount importance. As such, I've implemented a rigorous testing regime spanning various methodologies to verify every nuance of the system. Overall, a total of 35 tests have been constructed to cover every conceivable scenario.

### 1. Unit Testing

Unit testing focuses on testing individual components or units of the system in isolation. By isolating a specific segment of code and validating its correctness, I can ensure that every method and function works as intended.

- **Example**: Testing the functionality of the `JsonHandler` in the `common` module to verify that it accurately serializes and deserializes data.

### 2. Integration Testing

While unit tests focus on isolated parts, integration testing aims to ensure that these parts work harmoniously when integrated. It's essential to verify that interactions between different units result in the correct system behavior.

- **Example**: Testing the collaboration between `NetworkHandler` and `ContentServer` to ensure they communicate effectively over the network.

### 3. Regression Testing

Whenever new features are added or bugs are fixed, regression tests are run to make sure that these changes haven't inadvertently affected existing functionalities.

- **Example**: After a patch is applied to the `LoadBalancer`, tests are rerun to ensure its distribution logic still works as intended.

### 4. Functional Testing

Functional tests evaluate the system's functional requirements, ensuring that the system does what it's supposed to do.

- **Example**: A test might be constructed to verify that the `GETClient` retrieves the correct data based on a given ID.

### 5. Load Testing

Given the distributed nature of the system, it's vital to understand how the system behaves under substantial load. Load tests are designed to stress the system under test conditions to evaluate its performance.

- **Example**: Simulating multiple `GETClient` requests in quick succession to assess how the `LoadBalancer` manages high traffic.

### 6. End-to-End Testing

End-to-end tests validate the flow of an application from start to finish. They ensure that the entire process of user input and system processes results in the desired output.

- **Example**: Testing the entire workflow from a client sending a request, the `LoadBalancer` distributing it, and the `AggregationServer` processing and responding.

### Test Metrics:

- Total Number of Tests: 35
- Passed: (This would be variable, ideally close to 35 if all tests pass)
- Failed: (This would be variable based on the results of running the tests)

By combining these diverse testing methodologies, I are not only ensuring that individual components are functioning as intended but also that they interact harmoniously, delivering a robust and reliable system.

---

## Running the Project

### Compilation

1. **Main Program Compilation**
    - **Command**: `make all`
    - This command compiles the main source files. Internally, it calls the `compile-main` target.

2. **Test Compilation**
    - **Command**: `make compile-test`
    - This command is dedicated to compiling the test cases.

### Testing

1. **Running Tests**
    - **Command**: `make test`
    - This will execute all the tests. Initially, you might observe some exceptions thrown due to the nature of the tests. However, once the JUnit console standalone completes its execution, ensure you see `35 tests passing out of 35`. If not, there's likely an issue with the code.

2. **Clean Up**
    - **Command**: `make clean`
    - Use this command to clear all the compiled files, ensuring a fresh state for subsequent compilations and runs.

#### Address Exception:

If you encounter an exception that reads `Address already in use`, it suggests that another process on your machine or server is occupying the desired port. Here's what you can do:

1. **Update Port**: Navigate to the relevant configuration or code section and assign a different, available port for testing.

2. **Wait & Retry**: Sometimes, the port might get free in a short while. If you suspect this might be the case, wait for a few moments and execute the test command again.

It's essential to ensure that the port is available to avoid interference and obtain accurate test results.

### Running the System using `make` Commands

If you wish to witness the system in action using the predefined `make` commands, follow these steps:

1. **Default Load Balancer**
   - **Command**: `make loadbalancer`
   - Initializes the load balancer on `localhost` at port `4567` with 3 Aggregation Servers (AS).

2. **Specific Load Balancer Configurations**
   - **Command**: `make loadbalancer1` - Starts the load balancer on `localhost` at port `4567` with 1 AS.
   - **Command**: `make loadbalancer5` - Commences the load balancer on `localhost` at port `4567` with 5 AS.

3. **Content Servers**
   - Use these commands to send specific content files to a predefined server:
      - **Command**: `make content1` - Sends `input_v1.txt` to `localhost` at port `4567`.
      - **Command**: `make content2` - Sends `input_v2.txt` to `localhost` at port `4567`.
      - **Command**: `make content3` - Sends `input_v3.txt` to `localhost` at port `4567`.

4. **Clients**
   - Commands to initialize clients with various configurations:
      - **Command**: `make client1` - Starts a client connecting to `localhost:4567` without a specified `stationID`.
      - **Command**: `make client2` - Initiates a client targeting `http://localhost.domain.domain:4567` with a `stationID` of `IDS90210`.
      - **Command**: `make client3` - Launches a client connecting to `http://localhost:4567` with a `stationID` of `IDS60901`.

### Running the System using Direct `java` Commands

For a more customized setup and execution, use the direct `java` commands:

1. **Load Balancer**
   ```bash
   java -cp [your classpath here] main.aggregation.LoadBalancer [yourPortNumber] [numberOfAS]
   ```
   Example: `java -cp lib/gson-2.10.1.jar:out/ main.aggregation.LoadBalancer 8080 4`

2. **Content Servers**
   ```bash
   java -cp [your classpath here] main.content.ContentServer [serverName] [portNumber] [pathToYourInputFile]
   ```

3. **Clients**
   ```bash
   java -cp [your classpath here] main.client.GETClient [serverName:portNumber] [stationID]
   ```

Please note: The user needs to ensure they set the correct classpath (`[your classpath here]`) which should include all necessary libraries and the output directory where the compiled `.class` files are located. In this situation, it'd typically be `-cp lib/gson-2.10.1.jar:out/`.

Once familiarized with these commands, you can simulate a myriad of configurations and scenarios to gain a deeper understanding of the system's functionalities.


Thank you for exploring this project. Feedback and contributions are always welcome!
