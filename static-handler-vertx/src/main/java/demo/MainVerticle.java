package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

import java.time.Instant;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {

        var r = Router.router(vertx);

        r.get("/api/hello").handler(this::onHello);
        r.get("/api/hello/:name").handler(this::onHelloName);

        r.route().handler(StaticHandler.create("static"));

        getVertx().createHttpServer().requestHandler(r).listen(8080);
    }

    private void onHelloName(RoutingContext ctx) {
        var name = ctx.pathParam("name");
        ctx.request().response().end(String.format("Hi %s %s", name, Instant.now()));
    }

    private void onHello(RoutingContext ctx) {
        ctx.request().response().end("Hi Vertex " + Instant.now());
    }

}
