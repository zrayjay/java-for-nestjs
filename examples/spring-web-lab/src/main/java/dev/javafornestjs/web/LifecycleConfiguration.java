package dev.javafornestjs.web;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LifecycleConfiguration {
    public static final class Events {
        private final List<String> values = new CopyOnWriteArrayList<>();
        public void add(String value) { values.add(value); }
        public List<String> values() { return List.copyOf(values); }
    }
    public static final class Probe {
        private final Events events;
        public Probe(Events events) {
            this.events = events;
            events.add("construct");
        }
        public void start() { events.add("init"); }
        public void stop() { events.add("destroy"); }
    }
    @Bean
    Events lifecycleEvents() { return new Events(); }
    @Bean(initMethod = "start", destroyMethod = "stop")
    Probe lifecycleProbe(Events events) { return new Probe(events); }
    @Bean
    ApplicationListener<ApplicationReadyEvent> ready(Events events) {
        return event -> events.add("ready");
    }
}
