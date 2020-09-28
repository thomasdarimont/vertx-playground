package demo;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
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
                .compose(this::startServer)
                .setHandler(bootstrap);
    }

    private Future<Startup> initConfig(Promise<Void> bootstrap) {

        // load configuration from config.yaml file
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

    private Future<Startup> setupWebClient(Startup startup) {

        webClient = WebClient.create(vertx);

        return Promise.succeededPromise(startup).future();
    }

    private Future<Startup> setupJwtAuth(Startup startup) {

        var jwtConfig = startup.config.getJsonObject("jwt");
        var issuer = jwtConfig.getString("issuer");
        var issuerUri = URI.create(issuer);

        // derive JWKS uri from Keycloak issuer URI
        var jwksUri = URI.create(jwtConfig.getString("jwksUri", String.format("%s://%s:%d%s",
                issuerUri.getScheme(), issuerUri.getHost(), issuerUri.getPort(), issuerUri.getPath() + "/protocol/openid-connect/certs")));

        var promise = Promise.<JWTAuth>promise();

        // fetch JWKS from `/certs` endpoint
        webClient.get(jwksUri.getPort(), jwksUri.getHost(), jwksUri.getPath())
                .as(BodyCodec.jsonObject())
                .send(ar -> {

                    if (!ar.succeeded()) {
                        startup.bootstrap.fail(String.format("Could not fetch JWKS from URI: %s", jwksUri));
                        return;
                    }

                    var response = ar.result();

                    var jwksResponse = response.body();
                    var keys = jwksResponse.getJsonArray("keys");

                    // Configure JWT validation options
                    var jwtOptions = new JWTOptions();
                    jwtOptions.setIssuer(issuer);

                    // extract JWKS from keys array
                    var jwks = ((List<Object>) keys.getList()).stream()
                            .map(o -> new JsonObject((Map<String, Object>) o))
                            .collect(Collectors.toList());

                    // configure JWTAuth
                    var jwtAuthOptions = new JWTAuthOptions();
                    jwtAuthOptions.setJwks(jwks);
                    jwtAuthOptions.setJWTOptions(jwtOptions);
                    jwtAuthOptions.setPermissionsClaimKey(jwtConfig.getString("permissionClaimsKey", "realm_access/roles"));

                    JWTAuth jwtAuth = JWTAuth.create(vertx, jwtAuthOptions);
                    promise.complete(jwtAuth);
                });

        return promise.future().compose(auth -> {
            jwtAuth = auth;
            return Promise.succeededPromise(startup).future();
        });
    }

    private Future<Startup> setupRouter(Startup startup) {

        router = Router.router(vertx);

        router.route("/api/*").handler(JWTAuthHandler.create(jwtAuth));

        return Promise.succeededPromise(startup).future();
    }

    private Future<Startup> setupRoutes(Startup startup) {

        router.get("/api/greet").handler(this::handleGreet);
        router.get("/api/user").handler(this::handleUserData);
        router.get("/api/admin").handler(this::handleAdminData);

        return Promise.succeededPromise(startup).future();
    }

    private Future<Void> startServer(Startup startup) {

        var httpConfig = startup.config.getJsonObject("http");

        var port = httpConfig.getInteger("port", 3000);
        vertx.createHttpServer().requestHandler(router).listen(port);

        LOG.info("Vertx JWT-Service started!");

        return Promise.<Void>succeededPromise().future();
    }

    private void handleGreet(RoutingContext ctx) {

        var jwtUser = (JWTUser) ctx.user();
        var username = jwtUser.principal().getString("preferred_username");
        var userId = jwtUser.principal().getString("sub");

        var accessToken = ctx.request().getHeader(HttpHeaders.AUTHORIZATION).substring("Bearer ".length());
        // Use accessToken for down-stream calls...

        ctx.request().response().end(String.format("Hi %s (%s) %s%n", username, userId, Instant.now()));
    }

    private void handleUserData(RoutingContext ctx) {

        var jwtUser = (JWTUser) ctx.user();
        var username = jwtUser.principal().getString("preferred_username");
        var userId = jwtUser.principal().getString("sub");

        jwtUser.isAuthorized("user", res -> {

            if (!res.succeeded() || !res.result()) {
                toJsonResponse(ctx).setStatusCode(403).end("{\"error\": \"forbidden\"}");
                return;
            }

            JsonObject data = new JsonObject()
                    .put("type", "user")
                    .put("username", username)
                    .put("userId", userId)
                    .put("timestamp", Instant.now());

            toJsonResponse(ctx).end(data.toString());
        });
    }

    private void handleAdminData(RoutingContext ctx) {

        var jwtUser = (JWTUser) ctx.user();
        var username = jwtUser.principal().getString("preferred_username");
        var userId = jwtUser.principal().getString("sub");

        jwtUser.isAuthorized("admin", res -> {

            if (!res.succeeded() || !res.result()) {
                toJsonResponse(ctx).setStatusCode(403).end("{\"error\": \"forbidden\"}");
                return;
            }

            JsonObject data = new JsonObject()
                    .put("type", "admin")
                    .put("username", username)
                    .put("userId", userId)
                    .put("timestamp", Instant.now());

            toJsonResponse(ctx).end(data.toString());
        });
    }


    private HttpServerResponse toJsonResponse(RoutingContext ctx) {
        return ctx.request().response().putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
    }
}
