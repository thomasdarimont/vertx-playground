package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.impl.OAuth2AuthProviderImpl;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

import java.net.URI;
import java.time.Instant;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {

        Router router = Router.router(vertx);

        // Store session information on the server side
        SessionStore sessionStore = LocalSessionStore.create(vertx);
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        router.route().handler(sessionHandler);

        // Expose form parameters in request
        router.route().handler(BodyHandler.create());

        // CSRF handler setup required for logout form
        String csrfSecret = "zwiebelfische";
        router.route().handler(CSRFHandler.create(csrfSecret));

        // Used for backend calls with bearer token
        WebClient webClient = WebClient.create(vertx);

        String hostname = System.getProperty("http.host", "localhost");
        int port = Integer.getInteger("http.port", 8090);
        String baseUrl = String.format("http://%s:%d", hostname, port);
        String oauthCallbackPath = "/callback";

        // Our app is registered as a confidential OpenID Connect client with Authorization Code Flow in Keycloak,
        // thus we need to configure client_id and client_secret
        OAuth2ClientOptions clientOptions = new OAuth2ClientOptions()
                .setFlow(OAuth2FlowType.AUTH_CODE)
                .setSite(System.getProperty("oauth2.issuer", "http://localhost:8080/auth/realms/vertx"))
                .setClientID(System.getProperty("oauth2.client_id", "demo-client"))
                .setClientSecret(System.getProperty("oauth2.client_secret", "ff160ac9-6989-4940-8013-883e81b39702"));

        // We use Keycloaks OpenID Connect discovery endpoint to infer the Oauth2 / OpenID Connect endpoint URLs
        KeycloakAuth.discover(vertx, clientOptions, asyncResult -> {

            OAuth2Auth oauth2Auth = asyncResult.result();

            if (oauth2Auth == null) {
                throw new RuntimeException("Could not configure Keycloak integration via OpenID Connect Discovery Endpoint. Is Keycloak running?");
            }

            AuthHandler oauth2 = OAuth2AuthHandler.create(oauth2Auth, baseUrl + oauthCallbackPath) //
                    .setupCallback(router.get(oauthCallbackPath)) //
                    // Additional scopes: openid for OpenID Connect
                    .addAuthority("openid");

            // protect resources beneath /protected/* with oauth2 handler
            router.route("/protected/*").handler(oauth2);

            // configure route handlers
            configureRoutes(router, webClient, oauth2Auth);
        });


        getVertx().createHttpServer().requestHandler(router).listen(port);
    }

    private void configureRoutes(Router router, WebClient webClient, OAuth2Auth oauth2Auth) {

        router.get("/").handler(this::handleIndex);

        router.get("/protected/").handler(this::handleGreet);
        router.get("/protected/user").handler(this::handleUserPage);
        router.get("/protected/admin").handler(this::handleAdminPage);

        // extract discovered userinfo endpoint url
        String userInfoUrl = ((OAuth2AuthProviderImpl) oauth2Auth).getConfig().getUserInfoPath();
        router.get("/protected/userinfo").handler(createUserInfoHandler(webClient, userInfoUrl));

        router.post("/logout").handler(this::handleLogout);
    }

    private void handleIndex(RoutingContext ctx) {
        respondWithOk(ctx, "text/html", "<h1>Welcome to Vert.x Keycloak Example</h1><br><a href=\"/protected/\">Protected</a>");
    }

    private void handleUserPage(RoutingContext ctx) {

        OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();

        if (user == null) {
            respondWithServerError(ctx, "text/html", String.format("<h1>Request failed %s</h1>", "user missing"));
            return;
        }

        // extract username from IDToken, there are many more claims like (email, givenanme, familyname etc.) available
        String username = user.idToken().getString("preferred_username");

        String content = String.format("<h1>User Page: %s @%s</h1><a href=\"/protected/\">Protected Area</a>", username, Instant.now());
        respondWithOk(ctx, "text/html", content);
    }

    private void handleAdminPage(RoutingContext ctx) {

        OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();

        if (user == null) {
            respondWithServerError(ctx, "text/html", String.format("<h1>Request failed %s</h1>", "user missing"));
            return;
        }

        // check for realm-role "admin"
        user.isAuthorized("realm:admin", res -> {

            if (!res.succeeded() || !res.result()) {
                respondWith(ctx, 403, "text/html", "<h1>Forbidden</h1>");
                return;
            }

            String username = user.idToken().getString("preferred_username");

            String content = String.format("<h1>Admin Page: %s @%s</h1><a href=\"/protected/\">Protected Area</a>", username, Instant.now());
            respondWithOk(ctx, "text/html", content);
        });
    }

    private Handler<RoutingContext> createUserInfoHandler(WebClient webClient, String userInfoUrl) {

        return (RoutingContext ctx) -> {

            OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();

            if (user == null) {
                respondWithServerError(ctx, "text/html", String.format("<h1>Request failed %s</h1>", "user missing"));
                return;
            }

            // We use the userinfo endpoint as a straw man "backend" to demonstrate backend calls with bearer token
            URI userInfoEndpointUri = URI.create(userInfoUrl);
            webClient
                    .get(userInfoEndpointUri.getPort(), userInfoEndpointUri.getHost(), userInfoEndpointUri.getPath())
                    // use the access token for calls to other services protected via JWT Bearer authentication
                    .bearerTokenAuthentication(user.opaqueAccessToken())
                    .as(BodyCodec.jsonObject())
                    .send(ar -> {

                        if (!ar.succeeded()) {
                            respondWith(ctx, 500, "application/json", "{}");
                            return;
                        }

                        JsonObject body = ar.result().body();
                        respondWithOk(ctx, "application/json", body.encode());
                    });
        };
    }

    private void handleLogout(RoutingContext ctx) {

        OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();

        if (user == null) {
            respondWithServerError(ctx, "text/html", String.format("<h1>Request failed %s</h1>", "user missing"));
            return;
        }

        user.logout(res -> {

            if (!res.succeeded()) {
                // the user might not have been logged out, to know why:
                respondWithServerError(ctx, "text/html", String.format("<h1>Logout failed %s</h1>", res.cause()));
                return;
            }

            ctx.session().destroy();
            ctx.response().setStatusCode(302).putHeader("location", "/?logout=true").end();
        });
    }

    private void handleGreet(RoutingContext ctx) {

        OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();

        if (user == null) {
            respondWithServerError(ctx, "text/html", String.format("<h1>Logout failed %s</h1>", "user missing"));
            return;
        }

        String username = user.idToken().getString("preferred_username");
        String displayName = user.idToken().getString("name");

        String greeting = String.format("<h1>Hi %s (%s) @%s</h1><ul>" +
                "<li><a href=\"/protected/user\">User Area</a></li>" +
                "<li><a href=\"/protected/admin\">Admin Area</a></li>" +
                "<li><a href=\"/protected/userinfo\">User Info (Remote Call)</a></li>" +
                "</ul>", username, displayName, Instant.now());

        String logoutForm = createLogoutForm(ctx);

        respondWithOk(ctx, "text/html", greeting + logoutForm);
    }

    private String createLogoutForm(RoutingContext ctx) {

        String csrfToken = ctx.get(CSRFHandler.DEFAULT_HEADER_NAME);

        return "<form action=\"/logout\" method=\"post\" enctype='multipart/form-data'>"
                + String.format("<input type=\"hidden\" name=\"%s\" value=\"%s\">", CSRFHandler.DEFAULT_HEADER_NAME, csrfToken)
                + "<button>Logout</button></form>";
    }

    private void respondWithOk(RoutingContext ctx, String contentType, String content) {
        respondWith(ctx, 200, contentType, content);
    }

    private void respondWithServerError(RoutingContext ctx, String contentType, String content) {
        respondWith(ctx, 500, contentType, content);
    }

    private void respondWith(RoutingContext ctx, int statusCode, String contentType, String content) {
        ctx.request().response() //
                .setStatusCode(statusCode) //
                .putHeader("content-type", contentType) //
                .end(content);
    }
}
