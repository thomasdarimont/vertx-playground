package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {

        DeploymentOptions opts = new DeploymentOptions();
        opts.setInstances(4).setWorker(true);
        vertx.deployVerticle(HelloVerticle.class.getName(), opts);

        Router r = Router.router(vertx);
        r.get("/api/v1/hello").handler(this::onHello);
        r.get("/api/v1/hello/:name").handler(this::onHelloName);

        getVertx().createHttpServer().requestHandler(r).listen(Integer.getInteger("port", 8080));
    }

    private void onHelloName(RoutingContext ctx) {
        vertx.eventBus().request("hello.named.addr", ctx.pathParam("name"), reply -> {
            ctx.request().response().end((String) reply.result().body());
        });

    }

    private void onHello(RoutingContext ctx) {

        vertx.eventBus().request("hello.named.addr", "", reply -> {
            ctx.request().response().end((String) reply.result().body());
        });
    }

}
