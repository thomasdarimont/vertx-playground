package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {

        Router r = Router.router(vertx);
        r.get("/api/v1/hello").handler(this::onHello);
        r.get("/api/v1/hello/:name").handler(this::onHelloName);

        getVertx().createHttpServer().requestHandler(r).listen(8080);
    }

    private void onHelloName(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        ctx.request().response().end(String.format("Hi %s %s", name, Instant.now()));
    }

    private void onHello(RoutingContext ctx) {
        ctx.request().response().end("Hi Vertex " + Instant.now());
    }

}
