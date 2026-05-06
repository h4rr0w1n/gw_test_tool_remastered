# Verifying CTSW116 (Subcase 2) Payload on Solace AMQP Broker

When executing the second subcase of CTSW116 (which involves GZIP-compressed binary payloads), the Solace Pub/Sub+ Web UI may throw a "malformed URI" or parsing error. This is a known issue: the Web UI's built-in message viewer (such as the "Try-Me" tab or message peek feature) often struggles to safely render raw binary/GZIP-compressed byte arrays, or improperly attempts to parse binary AMQP properties as displayable strings.

Since the Web UI cannot be relied upon for visual verification in this specific scenario, follow the steps below to accurately verify that the payload is properly sent, published, and routable.

## 1. Verifying the Publish Route (Broker-Side Metrics)

Instead of inspecting the message content directly in the Web UI, use the broker's statistical metrics to confirm the message was successfully received and enqueued.

*   **Check Queue/Topic Spool Metrics:**
    1. Log into the Solace Pub/Sub+ Manager.
    2. Navigate to **Message VPNs** -> your active VPN -> **Queues** (or **Endpoints**, depending on your routing setup).
    3. Locate the target destination queue for CTSW116.
    4. Monitor the **Messages Queued** or **Current Spool Usage**. If the value increments by the exact number of messages sent, the broker has successfully received and persisted the AMQP message, proving the publish route works.
*   **Monitor Client Connections:**
    1. Navigate to **Client Connections**.
    2. Find the AMQP publisher client associated with the AMHS/SWIM Test Tool.
    3. Check the **Stats** tab for the client. Verify that **Client Data Messages Received** (by the broker) increments and there are no **Discarded Messages** or **Rx Errors** indicating AMQP frame rejections.

## 2. Working with the Subscribe Route (Programmatic Verification)

To verify the payload structure and properties without triggering the Web UI's rendering errors, you must consume the message using a programmatic AMQP subscriber. 

We have provided a standalone Python script `verify_ctsw116_consumer.py` that utilizes `qpid-proton` (the standard AMQP 1.0 library) to connect to Solace, consume the message, and dump the raw binary payload.

### Consumer Script Execution Guide

#### Prerequisites
1. Ensure you have Python 3 installed.
2. Install the Apache Qpid Proton library for AMQP 1.0:
   ```powershell
   pip install python-qpid-proton
   ```

#### Running the Script
1. Open your terminal in the project directory where `verify_ctsw116_consumer.py` is located.
2. Execute the script with your Solace broker URL and the target queue/address.
   **Format:**
   ```powershell
   python verify_ctsw116_consumer.py <amqp-url> <queue-or-topic-address>
   ```
   **Example:**
   ```powershell
   # Replace with your actual Solace AMQP port (default is usually 5672), credentials, and queue name
   python verify_ctsw116_consumer.py amqp://admin:password@localhost:5672 test_queue
   ```
3. Once running, trigger the CTSW116 subcase 2 from your AMHS/SWIM Gateway Test Tool.

#### What to Expect
When the script receives the message, it will output the message properties (including `content-type`) and automatically save the raw byte stream to a file named `ctsw116_payload_received.gz`. The script will then automatically close its connection.

```text
[*] Listening on amqp://admin:password@localhost:5672 for address/queue: test_queue

--- Message Received ---
Message ID: 12345
Content-Type: application/octet-stream
Properties: {'content-encoding': 'gzip', ...}
[*] Saved binary payload to ctsw116_payload_received.gz
[*] You can verify it by running: gzip -t ctsw116_payload_received.gz
```

## 3. Validating the Payload Integrity

Once `ctsw116_payload_received.gz` is saved:

1.  **Check AMQP Properties:** Ensure the terminal output from the script reflects a compressed payload (`content-type` is set to `application/octet-stream` and `content-encoding` or application properties reflect `gzip`).
2.  **Verify GZIP Integrity:** Attempt to decompress the dumped `.gz` file:
    *   **On Windows (Powershell):**
        You can use 7-Zip or another archiver, or programmatically extract it. Alternatively, use standard gzip if installed: `gzip -d ctsw116_payload_received.gz`.
    *   If the file decompresses into the expected File Transfer Body Part (FTBP) content without checksum errors, the payload was handled by Solace perfectly and the test case passes.

## Summary
The "malformed URI" error is a cosmetic and parsing limitation of the Solace Web UI when dealing with compressed binary streams. By validating through **broker spool metrics** and **programmatic AMQP consumption via the provided Python script**, you can confidently bypass the UI limitations and accurately verify protocol compliance and payload integrity for CTSW116.
