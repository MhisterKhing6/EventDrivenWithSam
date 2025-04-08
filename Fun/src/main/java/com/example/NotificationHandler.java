package com.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationHandler implements RequestHandler<S3Event, String> {
    private static final Logger logger = LoggerFactory.getLogger(NotificationHandler.class);
    private final SnsClient snsClient;
    private final String snsTopicArn;
    private final String environment;

    public NotificationHandler() {
        // Initialize the SNS client
        this.snsClient = SnsClient.builder()
                .region(Region.EU_WEST_1) // Update with your region
                .build();
                
        // Get environment variables
        this.snsTopicArn = System.getenv("SNS_TOPIC_ARN");
        this.environment = System.getenv("ENVIRONMENT");
        
        logger.info("NotificationHandler initialized with SNS topic: {}", snsTopicArn);
    }

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        logger.info("Received S3 event: {}", s3Event);
        
        try {
            // Process each record in the S3 event
            for (S3EventNotificationRecord record : s3Event.getRecords()) {
                String bucket = record.getS3().getBucket().getName();
                String key = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8);
                long size = record.getS3().getObject().getSizeAsLong();
                
                logger.info("Processing S3 object: bucket={}, key={}, size={}", bucket, key, size);
                
                // Create message content
                String message = String.format(
                    "A new file was uploaded to your %s environment\n" +
                    "Bucket: %s\n" +
                    "File: %s\n" +
                    "Size: %d bytes",
                    environment, bucket, key, size
                );
                
                // Send SNS notification
                PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(snsTopicArn)
                    .subject(String.format("[%s] New file uploaded: %s", environment.toUpperCase(), key))
                    .message(message)
                    .build();
                
                PublishResponse publishResponse = snsClient.publish(publishRequest);
                logger.info("SNS notification sent, message ID: {}", publishResponse.messageId());
            }
            
            return "Notification sent successfully!";
        } catch (Exception e) {
            logger.error("Error processing S3 event", e);
            throw new RuntimeException("Error processing S3 event", e);
        }
    }
}