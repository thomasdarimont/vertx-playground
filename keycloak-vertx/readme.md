Demo for Keycloak Vertx Integration
---

PoC for securing a Vertx App with Keycloak via OpenID Connect with Authorization Code Grant Flow 
with configuration via OpenID Connect Discovery Endpoint.  

An example realm `vertx` with a `demo-client` configuration can be found in the `vertx-realm.json`.
In my demo I created a user `tester` with password `test` and an admin user `vadmin` also with password `test`.  

Note: This example assumes Keycloak to be accessible via `http://localhost:8080/auth`.

# Features
The following features are demonstrated:  
- Login via OpenID Connect Authorization Code Flow Grant
- Logout with CSRF Token protection
- RBAC with Keycloak Realm roles
- Remote-Calls with Bearer token

# Prepare

## Start Keycloak and import the vertx-realm
docker run \
  -it \
  --name vertx-keycloak \
  --rm \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD=admin \
  -e KEYCLOAK_IMPORT=/tmp/vertx-realm.json \
  -v $PWD/vertx-realm.json:/tmp/vertx-realm.json \
  -p 8080:8080 \
  quay.io/keycloak/keycloak:9.0.0

## Users
The following users are available within Keycloak

master - Admin user
- admin:admin

vertx - User with role `user`
- tester:test

vertx - User with roles `user, admin`
- vadmin:test

# Build
```
mvn clean package
```

# Run
Run the following command and browse to `http://localhost:8090/`
```
java -jar target/*.jar
```
