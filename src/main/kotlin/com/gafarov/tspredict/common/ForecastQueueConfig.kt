package com.gafarov.tspredict.common

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(name = ["org.springframework.amqp.core.DirectExchange"])
@ConditionalOnProperty(
    prefix = "app.forecast-results",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class ForecastQueueConfig {

    @Bean
    fun forecastResultsExchange(
        @Value("\${app.forecast-results.exchange:tspredict.forecasts}") exchangeName: String
    ): DirectExchange {
        return DirectExchange(exchangeName, true, false)
    }

    @Bean
    fun forecastResultsQueue(
        @Value("\${app.forecast-results.queue:tspredict.forecast-results}") queueName: String
    ): Queue {
        return Queue(queueName, true)
    }

    @Bean
    fun forecastResultsBinding(
        forecastResultsQueue: Queue,
        forecastResultsExchange: DirectExchange,
        @Value("\${app.forecast-results.routing-key:forecast.result}") routingKey: String
    ): Binding {
        return BindingBuilder.bind(forecastResultsQueue)
            .to(forecastResultsExchange)
            .with(routingKey)
    }
}

@Configuration
@ConditionalOnClass(name = ["org.springframework.amqp.core.TopicExchange"])
@ConditionalOnProperty(
    prefix = "app.forecast-requests",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class ForecastRequestQueueConfig {

    @Bean
    fun forecastRequestsExchange(
        @Value("\${app.forecast-requests.exchange:tspredict.forecast-requests}") exchangeName: String
    ): TopicExchange {
        return TopicExchange(exchangeName, true, false)
    }
}
