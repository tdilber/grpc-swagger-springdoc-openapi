# Grpc Swagger Springdoc Openapi

Debugging gRPC clients with swagger-ui on Spring Boot 3.

Swagger for gRPC Clients.

**Note:** This project developed with springdoc-openapi v2.0.0 because This project Just supported on Spring Boot *
*v3.0.0** (
and above). I am also using this library net.devh:**grpc-spring-boot-starter**:2.14.0.RELEASE (thank you for that).

I am using on my open-source **microservice project**. You can see how to work.

https://github.com/tdilber/anouncy/blob/master/backend/location/src/main/java/com/beyt/anouncy/location/config/GrpcSwaggerConfig.java

## Usage:

**Just do this 3 step:**

### 1

```xml

<dependencies>
    ...
    <dependency>
        <groupId>com.beyt.doc</groupId>
        <artifactId>grpc-swagger-springdoc-openapi</artifactId>
        <version>0.0.1.1</version>
    </dependency>
</dependencies>

<repositories>
<repository>
    <id>github2</id>
    <name>GitHub Apache Maven Packages</name>
    <url>https://maven.pkg.github.com/tdilber/grpc-swagger-springdoc-openapi</url>
</repository>
</repositories>
```

**Note:** This dependency not working for public. I will serve with maven central repo as soon as possible. (Sorry for
that.)

### 2

Just Add the annotation on Spring Boot Application annotation.
(Example of 2 Service configuration).

There are 2 type of usage:

- createGrpcController => When exception occurred then throw exception.
- createGrpcControllerSafely => When exception occurred then create default class for not throw exception.

```java

@Configuration
public class GrpcSwaggerConfig {

    @Bean
    public GrpcRepositoryCreator grpcRepositoryCreator() {
        return new GrpcRepositoryCreator(); // use same creator because when same parameter classes usage will not create more than one. 
    }

    @Bean
    public Object createControllerVote(GrpcRepositoryCreator grpcRepositoryCreator) {
        return grpcRepositoryCreator.createGrpcController(VotePersistServiceGrpc.VotePersistServiceBlockingStub.class, //Grpc Stub Class 
                "persist-grpc-server", //grpc server config name
                "vote"); // prefix for same method name usage
    }

    @Bean
    public Object createControllerAnnounce(GrpcRepositoryCreator grpcRepositoryCreator) {
        return grpcRepositoryCreator.createGrpcControllerSafely(AnnouncePersistServiceGrpc.AnnouncePersistServiceBlockingStub.class, //Grpc Stub Class
                "persist-grpc-server", //grpc server config name
                "announce");  // prefix for same method name usage
    }
}
```

Sample Application Yaml File:

```yaml
grpc:
  client:
    persist-grpc-server: # the grpc server config name
      address: 'static://127.0.0.1:9096'
      enableKeepAlive: true
      keepAliveWithoutCalls: true
      negotiationType: plaintext
```

### 3

Standart swagger dependency for Spring Boot 3.

```xml

<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.0.0</version>
</dependency>
```

## How To Work

I used Byte-Buddy tool for creating RestController with POST endpoint for every gRPC service method.

- First I am converting to all method returns and method parameters objects (proto object what have builders) to POJOs.
- Second I am converting all services method in the single Rest Controller class with the POJOs. (With method
  interceptor.)
- Third intercetor does this 4 step =>

```
1- convert param POJO => Proto Object
2- invoke Proto Client With Proto Object (Returning Proto Object)
3- convert Returned Proto Object to Return POJO object
4- return The POJO object. 
```

**Note:** This conversion is using json serialization. 

