package demo;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> start) {

        var router = Router.router(vertx);
        router.get("/api/v1/hello").handler(this::onHello);
        router.get("/api/v1/hello/:name").handler(this::onHelloName);


        var yamlConfigOpts = new ConfigStoreOptions() //
                .setFormat("yaml") //
                .setType("file") //
                .setConfig(new JsonObject().put("path", "config.yaml"));

        var configRetrieverOpts = new ConfigRetrieverOptions() //
                .addStore(yamlConfigOpts);

        var configRetriever = ConfigRetriever.create(vertx, configRetrieverOpts);

        configRetriever.getConfig(ar -> handleConfigResults(ar, start, router));
    }

    private void handleConfigResults(AsyncResult<JsonObject> ar, Promise<Void> start, Router r) {

        if (ar.failed()) {
            start.fail("Could not parse configuration!");
            return;
        }

        JsonObject config = ar.result();

        Integer port = config.getJsonObject("http", new JsonObject()) //
                .getInteger("port", 8090);

        getVertx().createHttpServer().requestHandler(r).listen(port);

        start.complete();
    }

    private void onHelloName(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        ctx.request().response().end(String.format("Hi %s %s", name, Instant.now()));
    }

    private void onHello(RoutingContext ctx) {
        ctx.request().response().end("Hi Vertex " + Instant.now());
    }

}
