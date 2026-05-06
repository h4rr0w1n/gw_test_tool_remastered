import sys
import os
from proton.handlers import MessagingHandler
from proton.reactor import Container

def load_properties(filepath):
    """Simple parser for Java-style properties files."""
    props = {}
    if not os.path.exists(filepath):
        return props
    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if '=' in line:
                key, value = line.split('=', 1)
                # Handle escaped characters common in Java properties
                value = value.replace('\\:', ':').replace('\\=', '=')
                props[key.strip()] = value.strip()
    return props

class CTSW116Consumer(MessagingHandler):
    def __init__(self, server, address, vpn=None):
        super(CTSW116Consumer, self).__init__()
        self.server = server
        self.address = address
        self.vpn = vpn

    def on_start(self, event):
        # Connect to the AMQP broker
        # We pass the vpn as the virtual_host for Solace compatibility
        print(f"[*] Connecting to {self.server} (VPN: {self.vpn if self.vpn else 'default'})...")
        conn = event.container.connect(self.server, virtual_host=self.vpn)
        # Create a receiver for the specified queue/topic
        event.container.create_receiver(conn, self.address)
        print(f"[*] Listening for address/queue: {self.address}")

    def on_message(self, event):
        msg = event.message
        print("\n--- Message Received ---")
        print(f"Message ID: {msg.id}")
        print(f"Subject: {msg.subject}")
        print(f"Content-Type: {msg.content_type}")
        print(f"Application Properties: {msg.properties}")
        
        # Extract the body payload
        body_bytes = b""
        if isinstance(msg.body, bytes):
            body_bytes = msg.body
        elif isinstance(msg.body, str):
            body_bytes = msg.body.encode('utf-8')
        elif hasattr(msg.body, 'value'): # Handle AmqpValue wrappers
            val = msg.body.value
            if isinstance(val, bytes):
                body_bytes = val
            elif isinstance(val, str):
                body_bytes = val.encode('utf-8')
        
        # Save the payload if bytes were found
        if body_bytes:
            filename = "ctsw116_payload_received.gz"
            with open(filename, "wb") as f:
                f.write(body_bytes)
            print(f"\n[+] Saved binary payload to {filename} ({len(body_bytes)} bytes)")
            print(f"[*] Verification: Run 'gzip -t {filename}' to check integrity.")
        else:
            print("[-] No binary body payload could be extracted from the message.")
        
        # Stop after receiving one message
        event.connection.close()

    def on_transport_error(self, event):
        print(f"[-] Transport error: {event.transport.condition}")

    def on_connection_error(self, event):
        print(f"[-] Connection error: {event.connection.remote_condition}")

if __name__ == "__main__":
    # Load defaults from config/test.properties
    config_path = os.path.join("config", "test.properties")
    props = load_properties(config_path)
    
    def_host = props.get("swim.broker.host", "localhost")
    def_port = props.get("swim.broker.port", "5672")
    def_user = props.get("swim.broker.user", "default")
    def_pass = props.get("swim.broker.password", "default")
    def_vpn = props.get("swim.broker.vpn", "default")
    def_queue = props.get("gateway.default_queue", "TEST.QUEUE")

    # Construct default URL
    # amqp://user:pass@host:port
    default_url = f"amqp://{def_user}:{def_pass}@{def_host}:{def_port}"

    if len(sys.argv) < 2:
        print("--- CTSW116 Standalone Verifier ---")
        print(f"Usage: python verify_ctsw116_consumer.py <queue-address> [amqp-url] [vpn-name]")
        print(f"Defaults (from config):")
        print(f"  Queue:    {def_queue}")
        print(f"  URL:      {default_url}")
        print(f"  VPN:      {def_vpn}")
        print("-" * 35)
        
        # Use defaults if no arguments provided
        target_address = def_queue
        server_url = default_url
        vpn_name = def_vpn
    else:
        target_address = sys.argv[1]
        server_url = sys.argv[2] if len(sys.argv) > 2 else default_url
        vpn_name = sys.argv[3] if len(sys.argv) > 3 else def_vpn
    
    try:
        Container(CTSW116Consumer(server_url, target_address, vpn_name)).run()
    except KeyboardInterrupt:
        print("\n[*] Exiting consumer...")
    except Exception as e:
        print(f"[-] Fatal error: {e}")

