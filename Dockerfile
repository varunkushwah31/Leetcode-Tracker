# STAGE 1: Build the application
# Using Maven with Eclipse Temurin Java 25 to compile the code
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Copy the pom.xml and download dependencies first (this caches them!)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy your actual source code and build it
COPY src ./src
RUN mvn clean package -DskipTests

# STAGE 2: Run the application
# Using the lightweight Java 25 JRE to run the compiled jar
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built jar from Stage 1
COPY --from=build /app/target/*.jar app.jar

# Expose your backend port
EXPOSE 8080

# Run the app (Using sh -c to ensure environment variable expansion)
ENTRYPOINT ["sh", "-c", "echo '=== MONGO_URI ===' && echo $MONGO_URI && exec java -Dspring.data.mongodb.uri=\"$MONGO_URI\" -jar app.jar"]