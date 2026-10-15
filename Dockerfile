FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY src/ src/
COPY resources/ resources/
RUN mkdir classes && javac --release 17 -encoding UTF-8 -d classes src/gbm/*.java && jar --create --file app.jar --main-class gbm.App -C classes . -C resources .

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
COPY lib/postgresql.jar /app/lib/postgresql.jar
ENV BIND_HOST=0.0.0.0
ENV REQUIRE_DATABASE=true
EXPOSE 10000
USER 10001:10001
CMD ["java","-Xmx256m","-Djava.awt.headless=true","-cp","app.jar:lib/postgresql.jar","gbm.Server"]
