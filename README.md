# MySurvey Metagraph

## Setup

1. Build the Docker image:
   ```bash
   docker build -t mysurvey-metagraph .
   ```

2. Start the services using Docker Compose:
   ```bash
   docker-compose up -d
   ```

3. Deploy using Ansible:
   ```bash
   ansible-playbook deploy.yml -i inventory
   ```

## Usage

- Access the metagraph at `http://localhost:8080`
- Monitor logs using:
  ```bash
  docker logs -f mysurvey-metagraph
  ```

## Configuration

- Update `application.conf` for custom configurations.

## API Endpoints and Usage

1. **Create Survey**

   - **Endpoint**: `POST /surveys`
   - **Description**: Creates a new survey
   - **Request Body**:
     ```json
     {
       "creator": "DAG_ADDRESS",
       "questions": ["Question 1", "Question 2", "..."],
       "tokenReward": "100000000000000000",
       "imageUri": "https://example.com/survey-image.jpg"
     }
     ```
   - **Response**:
     - **Status**: `201 Created`
     - **Body**: `{ "surveyId": "UUID" }`

2. **Get Survey**

   - **Endpoint**: `GET /surveys/{surveyId}`
   - **Description**: Retrieves details of a specific survey
   - **Response**:
     ```json
     {
       "id": "UUID",
       "creator": "DAG_ADDRESS",
       "questions": ["Question 1", "Question 2", "..."],
       "tokenReward": "100000000000000000",
       "imageUri": "https://example.com/survey-image.jpg",
       "responseCount": 10
     }
     ```

3. **Submit Response**

   - **Endpoint**: `POST /surveys/{surveyId}/responses`
   - **Description**: Submits a response to a survey
   - **Request Body**:
     ```json
     {
       "respondent": "DAG_ADDRESS",
       "encryptedAnswers": "ENCRYPTED_STRING"
     }
     ```
   - **Response**:
     - **Status**: `201 Created`
     - **Body**: `{ "responseId": "UUID" }`

4. **Get Survey Statistics**

   - **Endpoint**: `GET /statistics`
   - **Description**: Retrieves global statistics about surveys
   - **Response**:
     ```json
     {
       "totalSurveys": 100,
       "totalResponses": 1000,
       "totalRewardsDistributed": "10000000000000000000"
     }
     ```

5. **Get User Rewards**

   - **Endpoint**: `GET /rewards/{address}`
   - **Description**: Retrieves the reward balance for a specific address
   - **Response**:
     ```json
     {
       "address": "DAG_ADDRESS",
       "rewardBalance": "1000000000000000000"
     }
     ```

6. **Withdraw Rewards**

   - **Endpoint**: `POST /withdraw`
   - **Description**: Initiates a withdrawal of rewards
   - **Request Body**:
     ```json
     {
       "address": "DAG_ADDRESS",
       "amount": "1000000000000000000"
     }
     ```
   - **Response**:
     - **Status**: `200 OK`
     - **Body**: `{ "transactionId": "TRANSACTION_HASH" }`

## Data Models

### Survey

scala
case class Survey(
id: UUID,
creator: Address,
questions: List[String],
tokenReward: BigInt,
imageUri: String
)



### SurveyResponse

scala
case class SurveyResponse(
surveyId: UUID,
respondent: Address,
encryptedAnswers: String
)


### SurveyCalculatedState

scala
case class SurveyCalculatedState(
surveys: Map[UUID, Survey],
responses: Map[UUID, List[SurveyResponse]],
rewards: Map[Address, BigInt],
totalSurveys: Int,
totalResponses: Int,
totalRewardsDistributed: BigInt
)


## Setup and Deployment Instructions

### Prerequisites:

- JDK 11 or higher
- SBT
- PostgreSQL database
- Access to a Constellation Network node

### Configuration:

Create an `application.conf` file in the `src/main/resources` directory:

hocon
mysurvey {
postgres-database {
url = "jdbc:postgresql://localhost:5432/surveydb"
user = "surveyuser"
password = "surveypw"
}
constellation-node {
url = "http://localhost:9000"
}
}


### Building the project:

bash
sbt clean compile


### Running tests:

bash
sbt test


### Packaging the application:

bash
sbt assembly


### Deploying to a Constellation Network node:

1. Copy the generated JAR file to your Constellation Network node.
2. Update the node's configuration to include your metagraph.
3. Restart the node.

### Monitoring:

- Check the node logs for any errors or warnings.
- Use the provided API endpoints to verify the metagraph is functioning correctly.

## Token Reward System Explanation

The survey metagraph implements a token reward system to incentivize survey creation and participation:

### Survey Creation:

- When a user creates a survey, they specify a `tokenReward` amount.
- This amount is deducted from the creator's balance and held in escrow by the metagraph.

### Survey Response:

- When a user responds to a survey, they are eligible for a reward.
- The reward is calculated based on the completeness of their response:
  ```scala
  val baseReward = survey.tokenReward / BigInt(survey.questions.size)
  val completionPercentage = response.encryptedAnswers.length.toDouble / survey.questions.size
  val earnedReward = (baseReward * BigDecimal(completionPercentage)).toBigInt
  ```

### Reward Distribution:

- Rewards are not immediately transferred to the respondent's wallet.
- Instead, they are recorded in the metagraph's state.
- Users can check their reward balance using the `/rewards/{address}` endpoint.

### Reward Withdrawal:

- Users can initiate a withdrawal of their rewards using the `/withdraw` endpoint.
- This triggers a token transfer on the Constellation Network.

### Rate Limiting:

- To prevent abuse, the system implements rate limiting on survey creation and response submission.
- Users are limited to a certain number of actions within a given time frame.

### Security Considerations:

- Survey responses are encrypted to ensure data privacy.
- Token operations (deductions and distributions) are atomic to ensure consistency.
- All token operations are validated and recorded on the Constellation Network for transparency and security.

This reward system aims to create a fair and incentivized ecosystem for survey creation and participation while maintaining the security and integrity of the Constellation Network.
