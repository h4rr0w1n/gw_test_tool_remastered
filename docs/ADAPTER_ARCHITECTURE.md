# AMHS-SWIM Gateway Dual-Adapter Architecture

## Overview

This implementation uses the **Adapter Pattern** to support both:
1. **Solace JCSMP** (proprietary API) - for legacy deployments
2. **Apache Qpid Proton-J** (standard AMQP 1.0) - for standards-compliant deployments

The system automatically detects which adapter to use at runtime, eliminating the need to choose one at build time.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    SwimDriver (Facade)                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  detectAndSelectAdapter()                              │  │
│  │  - Checks Solace availability first                    │  │
│  │  - Falls back to Qpid if Solace unavailable            │  │
│  └───────────────────────────────────────────────────────┘  │
│                          │                                   │
│          ┌───────────────┴───────────────┐                  │
│          ▼                               ▼                  │
│  ┌───────────────────┐           ┌───────────────────┐      │
│  │ SolaceSwimAdapter │           │  QpidSwimAdapter  │      │
│  │   (if available)  │           │   (fallback)      │      │
│  │                   │           │                   │      │
│  │ - JCSMP Session   │           │ - Proton-J        │      │
│  │ - XMLMessageProd. │           │ - Connection      │      │
│  │ - Topic routing   │           │ - Sender/Receiver │      │
│  └─────────┬─────────┘           └─────────┬─────────┘      │
│            │                               │                 │
└────────────┼───────────────────────────────┼─────────────────┘
             │                               │
    ┌────────┴────────┐             ┌────────┴────────┐
    │  Solace Broker  │             │ AMQP 1.0 Broker │
    │  (proprietary)  │             │  (standard)     │
    └─────────────────┘             └─────────────────┘
```

## Adapter Interface

All adapters implement `SwimMessagingAdapter`:

```java
public interface SwimMessagingAdapter {
    void connect() throws Exception;
    void publishMessage(String topic, byte[] payload, Map<String, Object> properties) throws Exception;
    byte[] consumeMessage(String address, long timeoutMs) throws Exception;
    void close();
    String getAdapterName();
    boolean isAvailable();  // Key method for auto-detection
}
```

## Auto-Detection Logic

When `SwimDriver.connect()` is called:

1. **Check Solace JCSMP**: 
   - Attempts to load `com.solace.client.JCSMPSession` class
   - If found → Use `SolaceSwimAdapter`
   
2. **Fallback to Qpid**:
   - Attempts to load `org.apache.qpid.proton.Proton` class
   - If found → Use `QpidSwimAdapter`
   
3. **Error**:
   - If neither is available → Throw `IllegalStateException`

### Code Flow

```java
private void detectAndSelectAdapter() {
    // Try Solace first (legacy/proprietary)
    SolaceSwimAdapter solaceAdapter = new SolaceSwimAdapter();
    if (solaceAdapter.isAvailable()) {
        this.activeAdapter = solaceAdapter;
        Logger.log("SUCCESS", "Selected Solace JCSMP adapter");
        return;
    }
    
    // Fallback to Qpid (standard AMQP 1.0)
    QpidSwimAdapter qpidAdapter = new QpidSwimAdapter();
    if (qpidAdapter.isAvailable()) {
        this.activeAdapter = qpidAdapter;
        Logger.log("SUCCESS", "Selected Qpid AMQP 1.0 adapter");
        return;
    }
    
    throw new IllegalStateException("No SWIM messaging adapter available");
}
```

## When to Use Each Adapter

### Solace JCSMP Adapter
- **Use when**: Deploying in environments with existing Solace message brokers
- **Pros**: 
  - Optimized for Solace appliances
  - Supports Solace-specific features (guaranteed messaging, etc.)
- **Cons**:
  - Proprietary API (vendor lock-in)
  - Requires Solace JCSMP libraries

### Qpid AMQP 1.0 Adapter
- **Use when**: Deploying in standards-compliant environments
- **Pros**:
  - Standard AMQP 1.0 (ICAO EUR Doc 047 compliant)
  - Works with any AMQP 1.0 broker (not just Solace)
  - No vendor lock-in
- **Cons**:
  - May not support Solace-specific optimizations

## Configuration

### pom.xml Dependencies

```xml
<!-- Always included (default fallback) -->
<dependency>
    <groupId>org.apache.qpid</groupId>
    <artifactId>proton-j</artifactId>
    <version>0.34.1</version>
</dependency>

<!-- Optional (if present, will be preferred) -->
<dependency>
    <groupId>com.solace.api</groupId>
    <artifactId>sol-jcsmp</artifactId>
    <version>10.20.0</version>
    <optional>true</optional>
</dependency>
```

### Runtime Behavior

| Scenario | Selected Adapter |
|----------|-----------------|
| Both Solace & Qpid present | Solace JCSMP |
| Only Qpid present | Qpid AMQP 1.0 |
| Only Solace present | Solace JCSMP |
| Neither present | Error |

## Testing

Test cases remain unchanged - they call `SwimDriver.publishMessage()` and `SwimDriver.consumeMessage()` without knowing which adapter is active.

To verify which adapter is being used:

```java
SwimDriver driver = new SwimDriver();
driver.connect();
System.out.println("Active adapter: " + driver.getActiveAdapterName());
// Output: "Active adapter: Solace-JCSMP" or "Active adapter: Qpid-AMQP1.0"
```

## Compliance

Both adapters support the required AMHS-SWIM properties per **ICAO EUR Doc 047, AMHS-SWIM Gateway Testing Plan V3.0**:

- `amhs_ats_pri` (SS/DD/FF/GG/KK priority)
- `amhs_recipients`
- `amhs_bodypart_type`
- `amhs_content_type`
- `amhs_originator`
- `amhs_subject`
- `amhs_message_id`
- `amhs_filing_time`
- `amhs_dl_history`
- `amhs_sec_envelope`

Each adapter maps these properties appropriately for its underlying protocol.

## Benefits of This Approach

1. **Flexibility**: Deploy in either Solace or standard AMQP environments without code changes
2. **Backward Compatibility**: Existing Solace deployments continue to work
3. **Standards Compliance**: New deployments can use standard AMQP 1.0
4. **No Build-Time Decision**: Adapter selection happens at runtime
5. **Graceful Degradation**: Automatically falls back if preferred adapter unavailable
6. **Easy Extension**: New adapters can be added by implementing `SwimMessagingAdapter`
