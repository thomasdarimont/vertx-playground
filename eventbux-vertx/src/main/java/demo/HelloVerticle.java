package demo;

import io.vertx.core.AbstractVerticle;

import java.time.Instant;

public class HelloVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.eventBus().consumer("hello.vertx.addr", msg -> {
            msg.reply("Hello Vertx World");
        });

        vertx.eventBus().consumer("hello.named.addr", msg -> {
            String name = (String) msg.body();
            msg.reply(String.format("Hello %s! %s @%s", name, Instant.now(), toString()));
        });
    }
}
