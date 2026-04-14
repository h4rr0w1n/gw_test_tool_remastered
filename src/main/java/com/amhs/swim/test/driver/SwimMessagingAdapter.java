package com.amhs.swim.test.driver;

import java.util.Map;

/**
 * Common interface for SWIM messaging adapters.
 * Allows runtime switching between Solace (legacy) and Qpid (standard AMQP 1.0).
 */
public interface SwimMessagingAdapter {

    /**
     * Kiểm tra xem Broker có sẵn sàng để kết nối hay không (TCP check).
     * @return true nếu host/port reachable, ngược lại false.
     */
    boolean canConnect();
    
    /**
     * Initialize the connection.
     * @throws Exception if connection fails
     */
    void connect() throws Exception;

    /**
     * Publish a message to the SWIM network.
     * @param topic The destination address/topic
     * @param payload The message body
     * @param properties AMHS/SWIM specific properties (priority, recipients, etc.)
     * @throws Exception if publishing fails
     */
    void publishMessage(String topic, byte[] payload, Map<String, Object> properties) throws Exception;

    /**
     * Publish a message to a specific topic.
     * @param topic The destination topic
     * @param payload The message body
     * @param properties AMHS/SWIM specific properties
     * @throws Exception if publishing fails
     */
    void publishToTopic(String topic, byte[] payload, Map<String, Object> properties) throws Exception;

    /**
     * Publish a message to a specific queue.
     * @param queue The destination queue
     * @param payload The message body
     * @param properties AMHS/SWIM specific properties
     * @throws Exception if publishing fails
     */
    void publishToQueue(String queue, byte[] payload, Map<String, Object> properties) throws Exception;

    /**
     * Consume a message from the SWIM network.
     * @param address The source address/queue to consume from
     * @param timeoutMs Timeout in milliseconds
     * @return The message payload, or null if timeout
     * @throws Exception if consuming fails
     */
    byte[] consumeMessage(String address, long timeoutMs) throws Exception;

    /**
     * Close the connection gracefully.
     */
    void close();

    /**
     * Returns the name of the adapter (for logging).
     */
    String getAdapterName();
    
    /**
     * Check if this adapter is available (dependencies present, broker reachable, etc.)
     * @return true if available, false otherwise
     */
    boolean isAvailable();
}
