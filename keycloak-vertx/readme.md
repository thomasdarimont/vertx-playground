Demo for Keycloak Vertx Integration
---

PoC for securing a Vertx App with Keycloak via OpenID Connect with Authorization Code Grant Flow 
with configuration via OpenID Connect Discovery Endpoint.  

An example realm `vertx` with a `demo-client` configuration can be found in the `vertx-realm-export.json`.
In my demo I created a user `tester` with password `test`.  

Note: This example assumes Keycloak to be accessible via `http://localhost:8080/auth`.

# Build
```
mvn clean package
```

# Run
Run the following command and browse to `http://localhost:8090/`
```
java -jar target/*.jar
```


