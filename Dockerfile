FROM maven:3.3-jdk-8-alpine

ENV EDDI_BASEURI http://localhost
ENV EDDI_PORT 7070

RUN mkdir -p /tests/
COPY . /tests/
RUN chmod -R 777 /tests/
WORKDIR /tests/

ENTRYPOINT mvn -Deddi.baseURI=$EDDI_BASEURI -Deddi.port=$EDDI_PORT test