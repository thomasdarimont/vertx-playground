package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.AuthHandler;
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

        // Used for backend calls with bearer token
        WebClient webClient = WebClient.create(vertx);

        // Store session information on the server side
        SessionStore sessionStore = LocalSessionStore.create(vertx);
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        router.route().handler(sessionHandler);

        String csrfSecret = "zwiebelfische";
        CSRFHandler csrfHandler = CSRFHandler.create(csrfSecret);
        router.route().handler(ctx -> {
                    // Ensure csrf token request parameter is available for CsrfHandler
                    // see Handling HTML forms https://vertx.io/docs/vertx-core/java/#_handling_requests
                    ctx.request().setExpectMultipart(true);
                    ctx.request().endHandler(v -> csrfHandler.handle(ctx));
                }
        );

        String hostname = System.getProperty("http.host", "localhost");
        int port = Integer.getInteger("http.port", 8090);
        String baseUrl = String.format("http://%s:%d", hostname, port);
        String oauthCallbackPath = "/callback";

        OAuth2ClientOptions clientOptions = new OAuth2ClientOptions()
                .setFlow(OAuth2FlowType.AUTH_CODE)
                // Issuer URL
                .setSite(System.getProperty("oauth2.issuer", "http://localhost:8080/auth/realms/vertx"))
                .setClientID(System.getProperty("oauth2.client_id", "demo-client"))
                .setClientSecret(System.getProperty("oauth2.client_secret", "1f88bd14-7e7f-45e7-be27-d680da6e48d8"));

        KeycloakAuth.discover(vertx, clientOptions, asyncResult -> {

            OAuth2Auth oauth2Auth = asyncResult.result();

            AuthHandler oauth2 = OAuth2AuthHandler.create(oauth2Auth, baseUrl + oauthCallbackPath) //
                    .setupCallback(router.get(oauthCallbackPath)) //
                    // Additional scopes
                    .addAuthority("openid");

            // session handler needs access to the authenticated user, otherwise we get an infinite redirect loop
            sessionHandler.setAuthProvider(oauth2Auth);

            router.route("/protected/*").handler(oauth2);

            router.get("/protected/user").handler(this::handleUserPage);
            router.get("/protected/admin").handler(this::handleAdminPage);
            router.get("/protected/userinfo").handler(createUserInfoHandler(webClient));
            router.get("/").handler(this::handleIndex);
            router.get("/protected").handler(this::handleGreet);
            router.post("/logout").handler(this::handleLogout);
        });


        getVertx().createHttpServer().requestHandler(router).listen(port);
    }

    private Handler<RoutingContext> createUserInfoHandler(WebClient webClient) {

        return (RoutingContext ctx) -> {

            OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();

            // We use the userinfo endpoint as a straw man "backend" to demonstrate backend calls with bearer token
            String userInfoEndpoint = user.accessToken().getString("iss") + "/protocol/openid-connect/userinfo";
            URI userInfoEndpointUri = URI.create(userInfoEndpoint);
            webClient
                    .get(userInfoEndpointUri.getPort(), userInfoEndpointUri.getHost(), userInfoEndpointUri.getPath())
                    .bearerTokenAuthentication(user.opaqueAccessToken())
                    .as(BodyCodec.jsonObject())
                    .send(ar -> {

                        if (ar.succeeded()) {
                            HttpResponse<JsonObject> response = ar.result();

                            JsonObject body = response.body();

                            ctx.request().response() //
                                    .putHeader("content-type", "application/json") //
                                    .end(body.encode());
                            return;
                        }

                        ctx.request().response() //
                                .putHeader("content-type", "application/json") //
                                .setStatusCode(500)
                                .end("{}");
                    });
        };
    }

    private void handleAdminPage(RoutingContext ctx) {

        OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();

        user.isAuthorized("realm:admin", res -> {

            if (res.succeeded() && res.result()) {
                String username = user.idToken().getString("preferred_username");

                String html = String.format("<h1>Admin Page: %s @%s</h1><a href=\"/protected\">Protected Area</a>", username, Instant.now());

                ctx.request().response() //
                        .putHeader("content-type", "text/html") //
                        .end(html);

                return;
            }

            ctx.request().response() //
                    .putHeader("content-type", "text/html") //
                    .setStatusCode(403)
                    .end("<h1>Forbidden</h1>");
        });
    }

    private void handleUserPage(RoutingContext ctx) {

        OAuth2TokenImpl user = (OAuth2TokenImpl) ctx.user();
        user.isAuthorized("realm:user", res -> {

            if (res.succeeded() && res.result()) {
                String username = user.idToken().getString("preferred_username");

                String html = String.format("<h1>User Page: %s @%s</h1><a href=\"/protected\">Protected Area</a>", username, Instant.now());

                ctx.request().response() //
                        .putHeader("content-type", "text/html") //
                        .end(html);
                return;
            }

            ctx.request().response() //
                    .putHeader("content-type", "text/html") //
                    .setStatusCode(403)
                    .end("<h1>Forbidden</h1>");
        });
    }

    private void handleLogout(RoutingContext ctx) {

        OAuth2TokenImpl oAuth2Token = (OAuth2TokenImpl) ctx.user();
        oAuth2Token.logout(res -> {
            if (res.succeeded()) {
                ctx.session().destroy();
                ctx.response().putHeader("location", "/?logout=true").setStatusCode(302).end();
            } else {
                // the user might not have been logged out
                // to know why:
                ctx.request().response().end(String.format("Logout failed %s", res.cause()));
            }
        });
    }

    private void handleGreet(RoutingContext ctx) {

        OAuth2TokenImpl oAuth2Token = (OAuth2TokenImpl) ctx.user();

        String username = oAuth2Token.idToken().getString("preferred_username");
        String displayName = oAuth2Token.idToken().getString("name");

        String greeting = String.format("<h1>Hi %s (%s) @%s</h1><ul>" +
                "<li><a href=\"/protected/user\">User Area</a></li>" +
                "<li><a href=\"/protected/admin\">Admin Area</a></li>" +
                "<li><a href=\"/protected/userinfo\">User Info (Remote Call)</a></li>" +
                "</ul>", username, displayName, Instant.now());

        String logoutForm = createLogoutForm(ctx);

        ctx.request().response() //
                .putHeader("content-type", "text/html") //
                .end(greeting + logoutForm);
    }

    private String createLogoutForm(RoutingContext ctx) {

        String csrfToken = ctx.get(CSRFHandler.DEFAULT_HEADER_NAME);

        return "<form action=\"/logout\" method=\"post\">"
                + String.format("<input type=\"hidden\" name=\"%s\" value=\"%s\"></input>", CSRFHandler.DEFAULT_HEADER_NAME, csrfToken)
                + "<button>Logout</button></form>";
    }

    private void handleIndex(RoutingContext ctx) {
        ctx.request().response() //
                .putHeader("content-type", "text/html") //
                .end("<h1>Welcome to Vertex Keycloak Example</h1><br><a href=\"/protected\">Protected</a>");
    }
}
