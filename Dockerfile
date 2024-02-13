FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY /build/libs/amt-deltaker-all.jar app.jar
EXPOSE 8080
CMD [ "-Xms256m", "-Xmx1024m", "-jar", "app.jar" ]
