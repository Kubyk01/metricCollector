FROM amazoncorretto:8-alpine

WORKDIR /app

COPY target/metric-collector-library-0.1.jar metric-collector-library.jar