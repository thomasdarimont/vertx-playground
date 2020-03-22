package demo;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.JWTAuthHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private WebClient webClient;

    private JWTAuth jwtAuth;

    private Router router;

    static class Startup {

        private final Promise<Void> bootstrap;

        private final JsonObject config;

        public Startup(Promise<Void> bootstrap, JsonObject config) {
            this.bootstrap = bootstrap;
            this.config = config;
        }
    }

    @Override
    public void start(Promise<Void> bootstrap) {

        initConfig(bootstrap)
                .compose(this::setupWebClient)
                .compose(this::setupJwtAuth)
                .compose(this::setupRouter)
                .compose(this::setupRoutes)
                .compose(this::startVertxServer)
                .setHandler(bootstrap);
    }

    Future<Void> startVertxServer(Startup startup) {

        int port = startup.config.getJsonObject("http").getInteger("port", 3000);
        vertx.createHttpServer().requestHandler(router).listen(port);

        LOG.info("Vertx JWT-Service started!");

        return Promise.<Void>succeededPromise().future();
    }

    Future<Startup> setupRoutes(Startup startup) {

        router.get("/api/greet").handler(this::handleGreet);
        router.get("/api/user").handler(this::handleUserData);
        router.get("/api/admin").handler(this::handleAdminData);

        return Promise.succeededPromise(startup).future();
    }

    Future<Startup> setupRouter(Startup startup) {

        router = Router.router(vertx);

        var jwtAuthHandler = JWTAuthHandler.create(jwtAuth);
        router.route("/api/*").handler(rc -> {
            jwtAuthHandler.handle(rc);
        });

        return Promise.succeededPromise(startup).future();
    }

    private Future<Startup> setupJwtAuth(Startup startup) {

        var issuer = startup.config.getJsonObject("jwt").getString("issuer");
        var issuerUri = URI.create(issuer);

        var jwksPath = issuerUri.getPath() + "/protocol/openid-connect/certs";

        var promise = Promise.<JWTAuth>promise();

        webClient.get(issuerUri.getPort(), issuerUri.getHost(), jwksPath)
                .as(BodyCodec.jsonObject())
                .send(ar -> {

                    if (!ar.succeeded()) {
                        startup.bootstrap.fail(String.format("Could not fetch JWKS from %s", jwksPath));
                        return;
                    }

                    var response = ar.result();

                    var jwksResponse = response.body();
                    var keys = jwksResponse.getJsonArray("keys");

                    var jwtAuthOptions = new JWTAuthOptions();
                    var jwtOptions = new JWTOptions();
                    jwtOptions.setIssuer(issuer);

                    jwtAuthOptions.setJWTOptions(jwtOptions);
                    var jwks = ((List<Object>) keys.getList()).stream()
                            .map(o -> new JsonObject((Map<String, Object>) o))
                            .collect(Collectors.toList());
                    jwtAuthOptions.setJwks(jwks);
                    jwtAuthOptions.setPermissionsClaimKey("realm_access/roles");

                    var jwtAuth = JWTAuth.create(vertx, jwtAuthOptions);
                    promise.complete(jwtAuth);
                });

        return promise.future().compose(auth -> {
            jwtAuth = auth;
            return Promise.succeededPromise(startup).future();
        });
    }

    Future<Startup> setupWebClient(Startup startup) {

        webClient = WebClient.create(vertx);

        return Promise.succeededPromise(startup).future();
    }

    Future<Startup> initConfig(Promise<Void> bootstrap) {

        var yamlConfigOpts = new ConfigStoreOptions() //
                .setFormat("yaml") //
                .setType("file") //
                .setConfig(new JsonObject().put("path", "config.yaml"));

        var configRetrieverOpts = new ConfigRetrieverOptions() //
                .addStore(yamlConfigOpts);

        var configRetriever = ConfigRetriever.create(vertx, configRetrieverOpts);

        return Future.future(configRetriever::getConfig)
                .map(config -> new Startup(bootstrap, config));
    }

    private void handleGreet(RoutingContext ctx) {

        var jwtUser = (JWTUser) ctx.user();
        var username = jwtUser.principal().getString("preferred_username");
        var userId = jwtUser.principal().getString("sub");

        ctx.request().response().end(String.format("Hi %s (%s) %s", username, userId, Instant.now()));
    }

    private void handleUserData(RoutingContext ctx) {

        var jwtUser = (JWTUser) ctx.user();
        var username = jwtUser.principal().getString("preferred_username");
        var userId = jwtUser.principal().getString("sub");

        jwtUser.isAuthorized("user", res -> {

            if (!res.succeeded() || !res.result()) {
                ctx.request().response().putHeader("content-type", "application/json").setStatusCode(403).end("{\"error\": \"forbidden\"}");
                return;
            }

            ctx.request().response().putHeader("content-type", "application/json").end(String.format("{data: \"User data %s (%s) %s\"}", username, userId, Instant.now()));
        });
    }

    private void handleAdminData(RoutingContext ctx) {

        var jwtUser = (JWTUser) ctx.user();
        var username = jwtUser.principal().getString("preferred_username");
        var userId = jwtUser.principal().getString("sub");

        jwtUser.isAuthorized("admin", res -> {

            if (!res.succeeded() || !res.result()) {
                ctx.request().response().putHeader("content-type", "application/json").setStatusCode(403).end("{\"error\": \"forbidden\"}");
                return;
            }

            ctx.request().response().putHeader("content-type", "application/json").end(String.format("{data: \"Admin data %s (%s) %s\"}", username, userId, Instant.now()));
        });
    }
}
