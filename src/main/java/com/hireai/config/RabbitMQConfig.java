package com.hireai.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMQConfig {

    // Exchange names
    public static final String HIRING_EXCHANGE = "hiring.exchange";
    public static final String HIRING_DLX = "hiring.dlx";

    // Queue names
    public static final String RESUME_PARSE_QUEUE = "resume.parse";
    public static final String RESUME_PARSE_DLQ = "resume.parse.dlq";
    public static final String CANDIDATE_SCORE_QUEUE = "candidate.score";
    public static final String CANDIDATE_SCORE_DLQ = "candidate.score.dlq";
    public static final String APPLICATION_SCREEN_QUEUE = "application.screen";
    public static final String APPLICATION_SCREEN_DLQ = "application.screen.dlq";
    public static final String INTERVIEW_EVALUATE_QUEUE = "interview.evaluate";
    public static final String INTERVIEW_EVALUATE_DLQ = "interview.evaluate.dlq";
    public static final String NOTIFICATION_QUEUE = "notification";

    // Routing keys
    public static final String RESUME_PARSE_KEY = "resume.parse";
    public static final String CANDIDATE_SCORE_KEY = "candidate.score";
    public static final String APPLICATION_SCREEN_KEY = "application.screen";
    public static final String INTERVIEW_EVALUATE_KEY = "interview.evaluate";
    public static final String NOTIFICATION_KEY = "notification.#";

    // --- Exchanges ---

    @Bean
    public TopicExchange hiringExchange() {
        return new TopicExchange(HIRING_EXCHANGE);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(HIRING_DLX);
    }

    // --- Queues ---

    @Bean
    public Queue resumeParseQueue() {
        return QueueBuilder.durable(RESUME_PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", HIRING_DLX)
                .withArgument("x-dead-letter-routing-key", RESUME_PARSE_QUEUE)
                .build();
    }

    @Bean
    public Queue resumeParseDlq() {
        return QueueBuilder.durable(RESUME_PARSE_DLQ).build();
    }

    @Bean
    public Queue candidateScoreQueue() {
        return QueueBuilder.durable(CANDIDATE_SCORE_QUEUE)
                .withArgument("x-dead-letter-exchange", HIRING_DLX)
                .withArgument("x-dead-letter-routing-key", CANDIDATE_SCORE_QUEUE)
                .build();
    }

    @Bean
    public Queue candidateScoreDlq() {
        return QueueBuilder.durable(CANDIDATE_SCORE_DLQ).build();
    }

    @Bean
    public Queue applicationScreenQueue() {
        return QueueBuilder.durable(APPLICATION_SCREEN_QUEUE)
                .withArgument("x-dead-letter-exchange", HIRING_DLX)
                .withArgument("x-dead-letter-routing-key", APPLICATION_SCREEN_QUEUE)
                .build();
    }

    @Bean
    public Queue applicationScreenDlq() {
        return QueueBuilder.durable(APPLICATION_SCREEN_DLQ).build();
    }

    @Bean
    public Queue interviewEvaluateQueue() {
        return QueueBuilder.durable(INTERVIEW_EVALUATE_QUEUE)
                .withArgument("x-dead-letter-exchange", HIRING_DLX)
                .withArgument("x-dead-letter-routing-key", INTERVIEW_EVALUATE_QUEUE)
                .build();
    }

    @Bean
    public Queue interviewEvaluateDlq() {
        return QueueBuilder.durable(INTERVIEW_EVALUATE_DLQ).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    // --- Bindings: main exchange -> queues ---

    @Bean
    public Binding resumeParseBinding() {
        return BindingBuilder.bind(resumeParseQueue()).to(hiringExchange()).with(RESUME_PARSE_KEY);
    }

    @Bean
    public Binding candidateScoreBinding() {
        return BindingBuilder.bind(candidateScoreQueue()).to(hiringExchange()).with(CANDIDATE_SCORE_KEY);
    }

    @Bean
    public Binding applicationScreenBinding() {
        return BindingBuilder.bind(applicationScreenQueue()).to(hiringExchange()).with(APPLICATION_SCREEN_KEY);
    }

    @Bean
    public Binding interviewEvaluateBinding() {
        return BindingBuilder.bind(interviewEvaluateQueue()).to(hiringExchange()).with(INTERVIEW_EVALUATE_KEY);
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(hiringExchange()).with(NOTIFICATION_KEY);
    }

    // --- Bindings: DLX -> DLQs ---

    @Bean
    public Binding resumeParseDlqBinding() {
        return BindingBuilder.bind(resumeParseDlq()).to(deadLetterExchange()).with(RESUME_PARSE_QUEUE);
    }

    @Bean
    public Binding candidateScoreDlqBinding() {
        return BindingBuilder.bind(candidateScoreDlq()).to(deadLetterExchange()).with(CANDIDATE_SCORE_QUEUE);
    }

    @Bean
    public Binding applicationScreenDlqBinding() {
        return BindingBuilder.bind(applicationScreenDlq()).to(deadLetterExchange()).with(APPLICATION_SCREEN_QUEUE);
    }

    @Bean
    public Binding interviewEvaluateDlqBinding() {
        return BindingBuilder.bind(interviewEvaluateDlq()).to(deadLetterExchange()).with(INTERVIEW_EVALUATE_QUEUE);
    }

    // --- Message converter & template ---

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());

        RetryTemplate retryTemplate = new RetryTemplate();
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOff);
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        template.setRetryTemplate(retryTemplate);

        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(5);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
