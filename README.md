# Java Programming with Dev Container
> - This is a Java project with DevContainer, Langchain4j, Vert-x, Docker Model Runner

## Run the application with Docker Compose

```bash
cd langchain4j-webapp-demo
docker compose up --build
```

and open [http://localhost:8888](http://localhost:8888)

## Run the application without Docker Compose

```bash
cd langchain4j-webapp-demo
mvn clean compile exec:java
```

and open [http://localhost:8888](http://localhost:8888)
