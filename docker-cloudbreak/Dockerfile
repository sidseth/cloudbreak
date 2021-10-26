FROM docker-private.infra.cloudera.com/cloudera_base/cldr-java:11.0.6-cldr1-jdk-slim-buster
MAINTAINER info@hortonworks.com

# REPO URL to download jar
ARG REPO_URL=https://repo.hortonworks.com/content/repositories/releases
ARG VERSION=''2.49.0-b57-3

ENV VERSION ${VERSION}

WORKDIR /

# install the cloudbreak app
ADD cloudbreak.jar /cloudbreak.jar

# add jmx exporter
ADD jmx_prometheus_javaagent-0.10.jar /jmx_prometheus_javaagent.jar

# extract schema files
RUN jar xf cloudbreak.jar && mv /BOOT-INF/classes/schema/ /schema/

# Install starter script for the Cloudbreak application
COPY bootstrap/start_cloudbreak_app.sh /
COPY bootstrap/wait_for_cloudbreak_api.sh /

ENTRYPOINT ["/start_cloudbreak_app.sh"]
