import sys
from proton.handlers import MessagingHandler
from proton.reactor import Container

class CTSW116Consumer(MessagingHandler):
    def __init__(self, server, address):
        super(CTSW116Consumer, self).__init__()
        self.server = server
        self.address = address

    def on_start(self, event):
        # Connect to the Solace broker
        conn = event.container.connect(self.server)
        # Create a receiver for the specified queue/topic
        event.container.create_receiver(conn, self.address)
        print(f"[*] Listening on {self.server} for address/queue: {self.address}")

    def on_message(self, event):
        msg = event.message
        print("\n--- Message Received ---")
        print(f"Message ID: {msg.id}")
        print(f"Content-Type: {msg.content_type}")
        print(f"Properties: {msg.properties}")
        
        # Extract the body payload
        body_bytes = b""
        if isinstance(msg.body, bytes):
            body_bytes = msg.body
        elif isinstance(msg.body, str):
            body_bytes = msg.body.encode('utf-8')
        else:
            print("Warning: Body is not standard raw bytes. Attempting cast...")
            try:
                body_bytes = bytes(msg.body)
            except Exception as e:
                print(f"Failed to cast body to bytes: {e}")
                
        # Save the payload if bytes were found
        if body_bytes:
            filename = "ctsw116_payload_received.gz"
            with open(filename, "wb") as f:
                f.write(body_bytes)
            print(f"[*] Saved binary payload to {filename}")
            print(f"[*] You can verify its integrity by running: gzip -t {filename} (or decompressing it manually)")
        else:
            print("[-] No binary body payload could be extracted from the message.")
        
        # Stop after receiving one message to inspect
        event.connection.close()

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python verify_ctsw116_consumer.py <amqp-url> <queue-or-topic-address>")
        print("Example: python verify_ctsw116_consumer.py amqp://admin:admin@localhost:5672 my_queue")
        sys.exit(1)
        
    server_url = sys.argv[1]
    target_address = sys.argv[2]
    
    try:
        Container(CTSW116Consumer(server_url, target_address)).run()
    except KeyboardInterrupt:
        print("\n[*] Exiting consumer...")
