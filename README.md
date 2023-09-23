# Project Overview

This project is structured to maintain modularity and separation of concerns, ensuring each component's responsibilities are clearly delineated.

## Directory Structure

The project's primary source code is located within the `src` directory. This directory is further divided into `main` and `test` sub-directories, each dedicated to the primary codebase and test cases, respectively.

### Main

Within the `main` directory, you'll find the following modules:
- `aggregation`: Contains the code related to the aggregation functionalities.
- `content`: Houses the source files for content management and handling.
- `client`: This module is responsible for the client-side operations.
- `common`: A shared directory, which contains utility and common functionalities.
- `network`: Focuses on networking aspects, including socket handling and network communication.

### Test

The `test` directory similarly houses test cases for the modules found in `main`, ensuring each functionality is verified for correctness.

## Running the Project

To execute the project, follow these steps:

1. Compile all source files:
    ```bash
    make all
    ```

2. In a separate terminal instance, launch the aggregation server:
    ```bash
    make aggregation
    ```

3. In another terminal, start the content server:
    ```bash
    make content
    ```

4. Finally, in yet another terminal, run the client:
    ```bash
    make client
    ```

## Running Tests

1. Compile the test cases using:
    ```bash
    make test
    ```

2. Once the tests are compiled, execute them using:
    ```bash
    make test_run
    ```

Thank you for exploring this project. Feedback and contributions are always welcome!
