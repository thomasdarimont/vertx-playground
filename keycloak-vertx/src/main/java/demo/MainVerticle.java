package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

import java.time.Instant;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {

        Router router = Router.router(vertx);

        // Store session information on the server side
        SessionStore sessionStore = LocalSessionStore.create(vertx);
        SessionHandler sessionHandler = SessionHandler.create(sessionStore);
        router.route().handler(sessionHandler);

        String hostname = System.getProperty("http.host", "localhost");
        int port = Integer.getInteger("http.port", 8090);
        String baseUrl = String.format("http://%s:%d", hostname, port);
        String oauthCallbackPath = "/callback";

        OAuth2ClientOptions clientOptions = new OAuth2ClientOptions()
                .setFlow(OAuth2FlowType.AUTH_CODE)
                // Issuer URL
                .setSite("http://localhost:8080/auth/realms/vertx")
                .setClientID("demo-client")
                .setClientSecret("1f88bd14-7e7f-45e7-be27-d680da6e48d8");

        KeycloakAuth.discover(vertx, clientOptions, asyncResult -> {

            OAuth2Auth oauth2Auth = asyncResult.result();

            AuthHandler oauth2 = OAuth2AuthHandler.create(oauth2Auth, baseUrl + oauthCallbackPath) //
                    .setupCallback(router.get(oauthCallbackPath)) //
                    // Additional scopes
                    .addAuthority("openid");

            // session handler needs access to the authenticated user, otherwise we get an infinite redirect loop
            sessionHandler.setAuthProvider(oauth2Auth);

            router.route("/protected/*").handler(oauth2);

            router.get("/").handler(this::handleIndex);
            router.get("/protected").handler(this::handleGreet);
        });


        getVertx().createHttpServer().requestHandler(router).listen(port);
    }

    private void handleGreet(RoutingContext ctx) {

        OAuth2TokenImpl oAuth2Token = (OAuth2TokenImpl) ctx.user();

        String username = oAuth2Token.idToken().getString("preferred_username");

        ctx.request().response().end(String.format("Hi %s @%S", username, Instant.now()));
    }

    private void handleIndex(RoutingContext ctx) {
        ctx.request().response() //
                .putHeader("content-type", "text/html") //
                .end("<h1>Welcome to Vertex Keycloak Example</h1><br><a href=\"/protected\">Protected</a>");
    }
}
