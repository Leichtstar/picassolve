FROM eclipse-temurin:17-jre
WORKDIR /app
COPY app.jar /app/app.jar

EXPOSE 8099
ENTRYPOINT ["sh", "-lc", "exec java -jar /app/app.jar"]
