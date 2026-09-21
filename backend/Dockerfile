# ---- Etapa de compilación ----
# Compila el JAR con Maven y JDK 25.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn/ .mvn/
# Descarga dependencias en una capa aparte para aprovechar la caché.
RUN mvn -B -q dependency:go-offline
COPY src/ src/
RUN mvn -B -q clean package -DskipTests

# ---- Etapa de ejecución ----
# Imagen ligera solo con el runtime de Java 25.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/pos-backend-*.jar app.jar
EXPOSE 8080
# El perfil y las credenciales se inyectan por variables de entorno en el despliegue.
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
