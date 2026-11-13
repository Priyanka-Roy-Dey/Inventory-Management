package com.inventory.api.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where low-stock events are published (INV-17). Without a topic ARN, as locally and in most tests, nothing is
 * published and the events stay in the outbox, where the Alerts page reads them (A41).
 */
@ConfigurationProperties("inventory.alerts")
public record AlertPublishingProperties(String topicArn, String region) {
}
