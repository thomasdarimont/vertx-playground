Demo for Vert.x JWT Auth with Keycloak
---

PoC for securing a Vert.x Service with JWT Bearer token Authentication.  

An example realm `vertx` with a `vertx-service` client configuration can be found in the `vertx-realm.json`.
In my demo I created a user `tester` with password `test` and an admin user `vadmin` also with password `test`.  

Note: This example assumes Keycloak to be accessible via `http://localhost:8080/auth`.

# Features
The following features are demonstrated:  
- Protecting a Vert.x app with JWT Authentication
- Extraction of JWT Claims
- RBAC with Keycloak Realm roles

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
Run the following command and browse to `http://localhost:3000/`
```
java -jar target/*.jar
```


# Test

In order to test the JWT bearer authentication we obtain an Access Token via Resource Owner Password Credentials Grant
Flow (ROPC) for the `vertx-service` client. To demonstrate role based access control we use the two users `tester` with 
realm role `user` and `vadmin` with realm role `admin`, both with password `test`.  

## Obtain access token from Keycloak for user `tester`
```
KC_USERNAME=tester
KC_PASSWORD=test
KC_CLIENT=vertx-service
KC_CLIENT_SECRET=ecb85cc5-f90d-4a03-8fac-24dcde57f40c
KC_REALM=vertx
KC_URL=http://localhost:8080/auth
KC_RESPONSE=$(curl  -k \
        -d "username=$KC_USERNAME" \
        -d "password=$KC_PASSWORD" \
        -d 'grant_type=password' \
        -d "client_id=$KC_CLIENT" \
        -d "client_secret=$KC_CLIENT_SECRET" \
        "$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" \
    | jq .)

KC_ACCESS_TOKEN=$(echo $KC_RESPONSE| jq -r .access_token)
echo $KC_ACCESS_TOKEN
```

## Call service endpoints as user `tester`

```
curl -v -H "Authorization: Bearer $KC_ACCESS_TOKEN" http://localhost:3000/api/greet

curl -v -H "Authorization: Bearer $KC_ACCESS_TOKEN" http://localhost:3000/api/user

curl -v -H "Authorization: Bearer $KC_ACCESS_TOKEN" http://localhost:3000/api/admin
```

## Obtain access token from Keycloak for user `vadmin`
```
KC_USERNAME=vadmin
KC_PASSWORD=test
KC_CLIENT=vertx-service
KC_CLIENT_SECRET=ecb85cc5-f90d-4a03-8fac-24dcde57f40c
KC_REALM=vertx
KC_URL=http://localhost:8080/auth
KC_RESPONSE=$(curl  -k \
        -d "username=$KC_USERNAME" \
        -d "password=$KC_PASSWORD" \
        -d 'grant_type=password' \
        -d "client_id=$KC_CLIENT" \
        -d "client_secret=$KC_CLIENT_SECRET" \
        "$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" \
    | jq .)

KC_ACCESS_TOKEN=$(echo $KC_RESPONSE| jq -r .access_token)
echo $KC_ACCESS_TOKEN
```

## Call service endpoints as user `vadmin`

```
curl -v -H "Authorization: Bearer $KC_ACCESS_TOKEN" http://localhost:3000/api/greet

curl -v -H "Authorization: Bearer $KC_ACCESS_TOKEN" http://localhost:3000/api/user

curl -v -H "Authorization: Bearer $KC_ACCESS_TOKEN" http://localhost:3000/api/admin
```