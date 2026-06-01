# AMHS/SWIM Gateway Test Tool - Docker Documentation

## Overview

This document explains how to build and run the AMHS/SWIM Gateway Test Tool as a Docker container.

## Prerequisites

- Docker 20.10 or later
- Docker Compose 2.0 or later (optional, for easier management)

## Quick Start

### Option 1: Using Docker Compose (Recommended)

1. **Build and run the container:**
   ```bash
   docker-compose up --build
   ```

2. **Run in detached mode:**
   ```bash
   docker-compose up -d --build
   ```

3. **Stop the container:**
   ```bash
   docker-compose down
   ```

### Option 2: Using Docker Directly

1. **Build the Docker image:**
   ```bash
   docker build -t amhs-swim-test-tool:latest .
   ```

2. **Run the container:**
   ```bash
   # Interactive mode with volume mounts
   docker run -it --rm \
     -v $(pwd)/config:/app/config:ro \
     -v $(pwd)/materials:/app/materials:ro \
     -v $(pwd)/output:/app/output \
     --network host \
     amhs-swim-test-tool:latest
   ```

   ```bash
   # With specific test case arguments
   docker run -it --rm \
     -v $(pwd)/config:/app/config:ro \
     -v $(pwd)/materials:/app/materials:ro \
     -v $(pwd)/output:/app/output \
     --network host \
     amhs-swim-test-tool:latest --case CTSW001
   ```

## Configuration

### Volume Mounts

The following directories are mounted as volumes for easy configuration and data access:

| Host Path | Container Path | Purpose |
|-----------|----------------|---------|
| `./config` | `/app/config` | Configuration files (read-only) |
| `./materials` | `/app/materials` | Test payloads and materials (read-only) |
| `./output` | `/app/output` | Reports, logs, and test results |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xmx512m` | Java runtime options |
| `CONFIG_PATH` | `/app/config` | Path to configuration directory |

### Network Configuration

The container uses `host` networking mode by default to ensure direct access to AMHS MTA, SWIM Broker, and Directory Server. If you need to use bridge networking instead:

```bash
docker run -it --rm \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/materials:/app/materials:ro \
  -v $(pwd)/output:/app/output \
  -p 8080:8080 \
  amhs-swim-test-tool:latest
```

### Running the GUI with Docker (X11 Forwarding)

Because this tool is a **graphical application (Java Swing)**, running it inside Docker requires an X-Server on your host machine to display the UI.

**For Windows Users:**
1. Download and install [VcXsrv (XLaunch)](https://sourceforge.net/projects/vcxsrv/).
2. Start **XLaunch** from your Start menu.
3. Accept the defaults until the **"Extra Settings"** page.
4. **IMPORTANT:** Check the box that says **"Disable access control"**.
5. Finish the setup. You should see an X icon in your system tray.
6. Run `docker-compose up` or use the `docker run` command (which now uses `-e DISPLAY=host.docker.internal:0.0` automatically via Compose).

**For Linux Users:**
1. Run `xhost +local:docker` in your terminal to allow Docker containers to access your display.
2. Run the tool via `docker-compose up` or passing your native `$DISPLAY`.

### Run All Test Cases (Manual CLI)

```bash
docker run -it --rm \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/materials:/app/materials:ro \
  -v $(pwd)/output:/app/output \
  -e DISPLAY=host.docker.internal:0.0 \
  --network host \
  amhs-swim-test-tool:latest
```

### Access Container Shell

```bash
docker run -it --rm \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/materials:/app/materials:ro \
  --network host \
  --entrypoint /bin/bash \
  amhs-swim-test-tool:latest
```

## Building from Source

The Dockerfile uses a multi-stage build process:

1. **Stage 1 (Builder):** Uses Maven to compile and package the application
2. **Stage 2 (Runtime):** Creates a minimal JRE-based runtime image

This approach minimizes the final image size while ensuring all dependencies are properly included.

## Security Considerations

- The container runs as a non-root user (`amhs`) for improved security
- Configuration and material volumes are mounted as read-only
- Consider using Docker secrets for sensitive configuration data in production

## Troubleshooting

### Container fails to start

1. Check Docker logs:
   ```bash
   docker logs amhs-swim-test-tool
   ```

2. Verify configuration files are valid:
   ```bash
   docker run -it --rm \
     -v $(pwd)/config:/app/config:ro \
     --entrypoint /bin/bash \
     amhs-swim-test-tool:latest \
     -c "cat /app/config/test.properties"
   ```

### Network connectivity issues

1. Test network connectivity from within the container:
   ```bash
   docker run -it --rm \
     --network host \
     --entrypoint /bin/bash \
     amhs-swim-test-tool:latest \
     -c "ping -c 3 your-amhs-host"
   ```

2. Ensure firewall rules allow container access to test systems

### Permission issues with volumes

Ensure the output directory exists and has proper permissions:

```bash
mkdir -p ./output
chmod 755 ./output
```

## Integration with CI/CD

Example GitHub Actions workflow:

```yaml
name: Run Tests

on: [push]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Docker image
        run: docker build -t amhs-swim-test-tool:latest .
      
      - name: Run test cases
        run: |
          docker run --rm \
            -v ${{ github.workspace }}/config:/app/config:ro \
            -v ${{ github.workspace }}/output:/app/output \
            amhs-swim-test-tool:latest --case CTSW001 --batch
      
      - name: Upload test results
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: ./output
```

## Image Size Optimization

The multi-stage build produces an optimized image (~200MB). To further reduce size:

1. Use Alpine-based images (if compatible with native libraries)
2. Remove unnecessary files in the runtime stage
3. Use distroless images for production deployments

## Support

For additional support and documentation, refer to:
- `docs/README.md` - Main documentation
- `docs/huong-dan-su-dung.md` - Vietnamese usage guide
- `verifier_usage_guide.txt` - Verifier component guide
