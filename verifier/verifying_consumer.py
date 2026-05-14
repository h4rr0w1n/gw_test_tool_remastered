import sys
import os
# pyrefly: ignore [missing-import]
from proton.handlers import MessagingHandler
# pyrefly: ignore [missing-import]
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

class VerifyingConsumer(MessagingHandler):
    def __init__(self, server, address, vpn=None):
        super(VerifyingConsumer, self).__init__()
        self.server = server
        self.address = address
        self.vpn = vpn

    def on_start(self, event):
        # Connect to the AMQP broker
        # We pass the vpn as the virtual_host for Solace compatibility
        print(f"[*] Connecting to {self.server} (VPN: {self.vpn if self.vpn else 'default'})...")
        conn = event.container.connect(self.server, virtual_host=self.vpn)
        # Create a receiver for the specified queue/topic
        # This receiver is non-exclusive and can coexist with other subscribers (e.g., Solace TryMe)
        event.container.create_receiver(conn, self.address)
        print(f"[*] Listening for address/queue: {self.address}")
        print("[*] Waiting for messages. Press Ctrl+C to stop.")
        print("[*] Note: This verifier uses non-exclusive access and can run concurrently with other subscribers.")
        if "QUEUE" in self.address.upper():
            print("[!] Statement: For Queue addresses, messages are distributed among subscribers. Concurrent consumption of the SAME message by multiple subscribers (including Solace TryMe) is unavailable for queues.")
        else:
            print("[*] For Topic addresses, multiple subscribers can consume the same message concurrently.")

    def on_message(self, event):
        msg = event.message
        print("\n" + "="*60)
        print("--- Message Received ---")
        
        # Display standard message properties dynamically
        standard_attrs = ['id', 'user_id', 'address', 'subject', 'reply_to', 
                          'correlation_id', 'content_type', 'content_encoding', 
                          'expiry_time', 'creation_time', 'group_id', 
                          'group_sequence', 'reply_to_group_id', 'priority']
        
        print("Standard Properties:")
        for prop in standard_attrs:
            val = getattr(msg, prop, None)
            if val is None and prop == 'content_encoding':
                val = 'None'
            elif val is None:
                val = ""
            print(f"  {prop}: {val}")
            
        print("\nApplication Properties:")
        if msg.properties:
            for k, v in msg.properties.items():
                if isinstance(v, (list, tuple)) and len(v) > 0:
                    print(f"  {k}:")
                    for i in range(0, len(v), 8):
                        chunk = v[i:i+8]
                        line_str = " ".join(str(x) for x in chunk)
                        print(f"    (line {(i//8)+1}) {line_str}")
                elif isinstance(v, str) and k in ('amhs_recipients', 'amhs_address') and (',' in v or ' ' in v) and len(v) > 30:
                    print(f"  {k}:")
                    # Split by comma if present, else space
                    v_list = [x.strip() for x in v.split(',' if ',' in v else ' ') if x.strip()]
                    for i in range(0, len(v_list), 8):
                        chunk = v_list[i:i+8]
                        line_str = " ".join(str(x) for x in chunk)
                        print(f"    (line {(i//8)+1}) {line_str}")
                else:
                    print(f"  {k}: {v}")
        else:
            print("  None")
            
        print("\nMessage Annotations:")
        if msg.annotations:
            for k, v in msg.annotations.items():
                print(f"  {k}: {v}")
        else:
            print("  None")
            
        print("\nDelivery Annotations:")
        if msg.instructions:
            for k, v in msg.instructions.items():
                print(f"  {k}: {v}")
        else:
            print("  None")
        
        # Extract and show the body payload
        print("\n--- Payload ---")
        body_val = msg.body
        if hasattr(msg.body, 'value'):
            body_val = msg.body.value
            
        print(f"Payload Type: {type(body_val)}")
        print(f"Payload Data: {body_val}")
        print("="*60 + "\n")

    def on_transport_error(self, event):
        print(f"[-] Transport error: {event.transport.condition}")

    def on_connection_error(self, event):
        print(f"[-] Connection error: {event.connection.remote_condition}")

if __name__ == "__main__":
    # Load defaults from config/test.properties
    config_path = os.path.join("config", "test.properties")
    if not os.path.exists(config_path):
        config_path = os.path.join("..", "config", "test.properties")

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
        print("--- Universal AMQP Verifier ---")
        print(f"Usage: python verifying_consumer.py <queue-address> [amqp-url] [vpn-name]")
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
        Container(VerifyingConsumer(server_url, target_address, vpn_name)).run()
    except KeyboardInterrupt:
        print("\n[*] Exiting consumer...")
    except Exception as e:
        print(f"[-] Fatal error: {e}")
