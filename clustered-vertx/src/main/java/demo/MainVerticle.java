package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * java -jar target/*.jar -cluster -Djava.net.preferIPv4Stack=true -Dhttp.port=8081 &
 * java -jar target/*.jar -cluster -Djava.net.preferIPv4Stack=true -Dhttp.port=8082 &
 * java -jar target/*.jar -cluster -Djava.net.preferIPv4Stack=true -Dhttp.port=8083 &
 */
public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {

        vertx.deployVerticle(new HelloVerticle());

        Router r = Router.router(vertx);
        r.get("/api/v1/hello").handler(this::onHello);
        r.get("/api/v1/hello/:name").handler(this::onHelloName);

        getVertx().createHttpServer().requestHandler(r).listen(Integer.getInteger("http.port", 8080));
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
