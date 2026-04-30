package com.jobhunter.processing.worker

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class QueueListenerConfig {
  @Bean
  fun postgresQueueListener(dataSource: DataSource, scheduler: WorkerScheduler): PostgresQueueListener =
    PostgresQueueListener(dataSource, onNotify = { scheduler.tick() })
}
