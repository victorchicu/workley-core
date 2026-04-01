package ai.workley.core.chat.config;

import ai.workley.core.chat.model.Message;
import ai.workley.core.chat.model.Content;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Sinks;

@Configuration
public class RSocketConfiguration {
    @Bean
    Sinks.Many<Message<? extends Content>> chatSessionSink() {
        return Sinks.many().multicast().onBackpressureBuffer(8192, false);
    }
}
