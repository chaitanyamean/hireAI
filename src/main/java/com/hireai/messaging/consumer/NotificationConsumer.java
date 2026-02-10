package com.hireai.messaging.consumer;

import com.hireai.config.RabbitMQConfig;
import com.hireai.messaging.event.NotificationEvent;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE, concurrency = "1-2")
    public void handleNotification(NotificationEvent event, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        log.info("Notification sent to {}: [{}] {}", event.getRecipientEmail(), event.getType(), event.getSubject());
        log.info("Notification body: {}", event.getBody());
        // TODO: integrate JavaMailSender or webhook for real notifications
        channel.basicAck(tag, false);
    }
}
