# AMHS/SWIM Gateway Test Tool — Technical Review

## Scope
This document reviews the “AMHS/SWIM Gateway Test Tool” contained in this repository: a Java Swing desktop application that executes ICAO EUR Doc 047 Appendix A test cases (CTSW101–CTSW116), publishes/consumes AMQP messages against a SWIM broker, and exports session results to Excel.

Primary references:
- Project overview and build/run instructions: [README.md](file:///workspace/README.md)
- Architecture summary: [docs/README.md](file:///workspace/docs/README.md)

## What The Tool Is (And Is Not)
- It is an operator-driven test execution tool (GUI + adapters + case implementations), not a unit-test framework.
- The repository does not contain an automated test suite (no JUnit/Surefire configuration in [pom.xml](file:///workspace/pom.xml#L61-L97)); [DriverTest.java](file:///workspace/src/test/java/com/amhs/swim/test/DriverTest.java) is a `main()` stub rather than an automated test.

## Architecture Overview
### High-level components
- GUI (Swing): launches from [Main](file:///workspace/src/main/java/com/amhs/swim/test/Main.java) and uses [TestFrame](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java) for settings, case selection, execution, and report actions.
- Driver/Adapter layer:
  - Adapter selection and publish/consume API: [SwimDriver](file:///workspace/src/main/java/com/amhs/swim/test/driver/SwimDriver.java)
  - Solace adapter: [SolaceSwimAdapter](file:///workspace/src/main/java/com/amhs/swim/test/driver/SolaceSwimAdapter.java)
  - AMQP 1.0 adapter (Qpid): [QpidSwimAdapter](file:///workspace/src/main/java/com/amhs/swim/test/driver/QpidSwimAdapter.java)
- Configuration:
  - Loader/saver: [TestConfig](file:///workspace/src/main/java/com/amhs/swim/test/config/TestConfig.java)
  - External config file: [config/test.properties](file:///workspace/config/test.properties)
  - Default bundled config: [src/main/resources/config/test.properties](file:///workspace/src/main/resources/config/test.properties)
- Case payload defaults and user overrides:
  - Defaults: [config/default_case_payloads.xml](file:///workspace/config/default_case_payloads.xml)
  - Overrides: `config/case_payloads.json` (created/updated at runtime)
  - Manager: [CaseConfigManager](file:///workspace/src/main/java/com/amhs/swim/test/config/CaseConfigManager.java)
- Reporting:
  - Export is triggered from the Settings UI in [TestFrame](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L480-L516) and writes an `.xlsx` file via `ExcelReportExporter`.

### Build and runtime packaging
- Maven assembly produces a runnable jar-with-dependencies with `Main-Class` set to `com.amhs.swim.test.Main` in [pom.xml](file:///workspace/pom.xml#L72-L95).
- Convenience runners:
  - Linux/macOS: [run_tool.sh](file:///workspace/run_tool.sh)
  - Windows: [run_tool.bat](file:///workspace/run_tool.bat)

## Data Flow (Operational Behavior)
### Typical test session
1. Operator configures SWIM broker connection and defaults (topic/queue/originator) in the Settings dialog ([TestFrame](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L380-L477)).
2. Tool loads defaults from classpath config and applies overrides from `config/test.properties` ([TestConfig.loadConfig](file:///workspace/src/main/java/com/amhs/swim/test/config/TestConfig.java#L32-L54)).
3. When a test publishes/consumes messages, [SwimDriver](file:///workspace/src/main/java/com/amhs/swim/test/driver/SwimDriver.java) auto-selects the active adapter:
   - Prefer Solace if present and reachable, else fall back to Qpid AMQP 1.0.
4. Results are accumulated for the session and can be exported to Excel (Settings → “Export Report (.xlsx)” in [TestFrame](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L488-L505)).

### Standalone verifier (CTSW116)
The repo includes a separate verification path intended for CTSW116:
- Script: [verify_payload.sh](file:///workspace/verify_payload.sh)
- Python consumer: [verify_ctsw116_consumer.py](file:///workspace/verify_ctsw116_consumer.py)

## Configuration Review
### Config layering
- Defaults: `src/main/resources/config/test.properties` (inside the jar)
- Overrides: `config/test.properties` (external file)
- In-process changes: Settings “Save & Apply” persists values back to `config/test.properties` via [TestConfig.saveConfig](file:///workspace/src/main/java/com/amhs/swim/test/config/TestConfig.java#L88-L96).

### Key properties used across the codebase
- Broker connection: `swim.broker.host`, `swim.broker.port`, `swim.broker.user`, `swim.broker.password`, `swim.broker.vpn`
- Default targets: `gateway.default_topic`, `gateway.default_queue`, `gateway.default_originator`
- Broker profile: `amqp.broker.profile` (dot form) is used by some runtime logic (see “Potential errors”).

## Potential Errors / Defects Found
The issues below are based on direct code inspection and are likely to cause misconfiguration, incorrect behavior, or confusion during operation.

### 1) Inconsistent broker profile key name (dot vs underscore)
Evidence:
- GUI reads/writes `amqp_broker_profile` (underscore) in Settings:
  - Reads: [TestFrame](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L394-L399)
  - Writes: [TestFrame](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L469-L472)
- Qpid adapter logic checks `amqp.broker.profile` (dot) when deciding Solace-specific behavior:
  - [QpidSwimAdapter.isSolaceProfile](file:///workspace/src/main/java/com/amhs/swim/test/driver/QpidSwimAdapter.java#L824-L827)

Impact:
- An operator can select “SOLACE” in the GUI and see it saved to `config/test.properties` as `amqp_broker_profile=SOLACE` (as in the current sample config: [config/test.properties](file:///workspace/config/test.properties#L4-L9)), but the adapter code that reads the dot-form key will still treat the profile as “STANDARD”.
- This can disable Solace-specific sanitization / tuning paths (e.g., [QpidSwimAdapter.sanitizeForSolace](file:///workspace/src/main/java/com/amhs/swim/test/driver/QpidSwimAdapter.java#L844-L849)).

How to detect:
- Set profile to SOLACE in the GUI, save, then check whether behavior governed by `isSolaceProfile()` triggers; it may not.

### 2) Validator reads config keys that do not exist in the provided configuration
Evidence:
- Validator uses:
  - `gateway.max.message.size` in [Validator.validatePayloadSize](file:///workspace/src/main/java/com/amhs/swim/test/util/Validator.java#L17-L31)
  - `gateway.max.recipients` in [Validator.validateRecipientCount](file:///workspace/src/main/java/com/amhs/swim/test/util/Validator.java#L39-L53)
- The documented and shipped config uses:
  - `gateway.max_size` and `gateway.max_recipients` in [README.md](file:///workspace/README.md#L111-L113) and [config/test.properties](file:///workspace/config/test.properties#L14-L22)

Impact:
- Validation limits may be effectively disabled because `getIntProperty(..., 0)` returns 0 when the property is missing, and 0 is treated as “no limit” in both validator methods.
- This undermines the tool’s ability to pre-check message constraints (payload size and recipient count) before publishing.

### 3) Misleading error message when adapters exist but broker is unreachable
Evidence:
- [SwimDriver.detectAndSelectAdapter](file:///workspace/src/main/java/com/amhs/swim/test/driver/SwimDriver.java#L57-L63) throws:
  - “AMQP software/driver is missing: Connection refused...” when at least one adapter is available, but cannot connect.

Impact:
- Troubleshooting is harder because the message conflates “missing dependencies” with “broker is unreachable / credentials incorrect”.

### 4) Default broker port inconsistency (resource defaults vs code defaults)
Evidence:
- Bundled defaults specify a nonstandard port (e.g., `swim.broker.port=55555`) in [src/main/resources/config/test.properties](file:///workspace/src/main/resources/config/test.properties).
- Code fallback defaults specify `swim.broker.port=5672` in [TestConfig.setDefaults](file:///workspace/src/main/java/com/amhs/swim/test/config/TestConfig.java#L56-L66).

Impact:
- If the classpath resource cannot be loaded (or packaging changes), the application silently defaults to 5672, which may be wrong for Solace SMF deployments and lead to connection failures.

### 5) Plaintext secret storage (operational risk)
Evidence:
- `swim.broker.password` and `directory.password` are stored and persisted in plaintext in [config/test.properties](file:///workspace/config/test.properties#L1-L22).
- GUI writes password back to config on save ([TestFrame](file:///workspace/src/main/java/com/amhs/swim/test/gui/TestFrame.java#L461-L472)).

Impact:
- This is not a functional bug, but it is a security/operational hazard in shared environments (source control, shared workstations, backups).

## Other Quality Observations (Not necessarily defects)
- `TestConfig.getIntProperty()` reads only from the loaded Properties object and does not honor `-D` overrides like `getProperty()` does ([TestConfig.getProperty](file:///workspace/src/main/java/com/amhs/swim/test/config/TestConfig.java#L98-L108) vs [getIntProperty](file:///workspace/src/main/java/com/amhs/swim/test/config/TestConfig.java#L110-L116)). If operators expect JVM system properties to override numeric settings, it will not work.
- There is no built-in automated regression safety net for adapter selection, config key consistency, and validation logic (see “What the tool is”).

## Recommendations (Non-breaking)
These are review recommendations only (this repository change request was “documents only”):
- Normalize configuration keys across GUI, adapters, and validators (choose one naming convention and support migration).
- Make connection failure messages explicit about root causes (DNS/port/auth/TLS/broker down vs missing libraries).
- Align default port behavior across resources and code fallbacks, and surface the active config values in the UI.
- Treat credentials as sensitive operational data (documentation guidance, ignore patterns, or optional secure stores).

