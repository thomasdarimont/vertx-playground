package demo;

import io.vertx.core.AbstractVerticle;

import java.time.Instant;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {
        getVertx().createHttpServer().requestHandler(req -> {
            req.response().end("Hi Vertex "  + Instant.now());
        }).listen(8080);
    }
}
