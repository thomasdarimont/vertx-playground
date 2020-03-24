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

## Start Keycloak with the vertx Realm
You can start a new Keycloak docker container with the prepared vertx realm by running the following command: 
```
docker run \
  -it \
  --name vertx-keycloak \
  --rm \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD=admin \
  -e KEYCLOAK_IMPORT=/tmp/vertx-realm.json \
  -v $PWD/vertx-realm.json:/tmp/vertx-realm.json \
  -v $PWD/data:/opt/jboss/keycloak/standalone/data \
  -p 8080:8080 \
  quay.io/keycloak/keycloak:9.0.2
```

Keycloak contains the following predefined users:  
- Master Realm Admin `admin:admin` user.
- Vertx Realm User `tester:test` with role `user`.
- Vertx Realm User `vadmin:test` with roles `user, admin`. 

# Build
```
mvn clean package
```

# Run
Run the following command and browse to `http://localhost:8090/`
```
java -jar target/*.jar
```
