# AMHS/SWIM Gateway Test Tool - Docker Image
# Multi-stage build for optimized image size

# Stage 1: Build stage
FROM maven:3.8-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy pom.xml first to leverage Docker cache for dependency download
COPY pom.xml .

# Download dependencies (this layer will be cached unless pom.xml changes)
RUN mvn dependency:go-offline -B || true

# Copy source code and resources
COPY src ./src
COPY lib ./lib
COPY config ./config
COPY cases.json .
COPY scripts ./scripts
COPY materials ./materials
COPY verifier ./verifier
COPY check_tables.py .
COPY update_cases.py .
COPY update_payloads.py .

# Build the application
# We need to install the local Solace JARs to local maven repo before building
RUN mvn install:install-file -Dfile=lib/sol-jcsmp-10.20.0.jar -DgroupId=com.solacesystems -DartifactId=sol-jcsmp -Dversion=10.20.0 -Dpackaging=jar && \
    mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:11-jre

# Install any runtime dependencies if needed (e.g., for native libraries)
# RUN apt-get update && apt-get install -y <packages> && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create non-root user for security
RUN groupadd -r amhs && useradd -r -g amhs amhs

# Copy built artifacts from builder stage
COPY --from=builder /build/target/*jar-with-dependencies.jar app.jar
COPY --from=builder /build/lib ./lib
COPY --from=builder /build/config ./config
COPY --from=builder /build/cases.json .
COPY --from=builder /build/scripts ./scripts
COPY --from=builder /build/materials ./materials
COPY --from=builder /build/verifier ./verifier
COPY --from=builder /build/check_tables.py ./check_tables.py
COPY --from=builder /build/update_cases.py ./update_cases.py
COPY --from=builder /build/update_payloads.py ./update_payloads.py

# Set ownership to non-root user
RUN chown -R amhs:amhs /app

# Switch to non-root user
USER amhs

# Set environment variables
ENV JAVA_OPTS="-Xmx512m"
ENV CONFIG_PATH="/app/config"

# Expose ports if the application needs them (adjust based on actual requirements)
# EXPOSE 8080

# Health check (optional - adjust based on application behavior)
# HEALTHCHECK --interval=30s --timeout=3s CMD java -cp app.jar com.amhs.swim.test.Main --health || exit 1

# Default command - run the test tool
# Pass arguments via docker run: docker run <image> <args>
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD []
