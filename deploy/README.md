# AI-chat Stage 5 candidate deployment

This deployment is deliberately passive. It disables Nacos discovery,
RabbitMQ topology declaration/listeners, all `@Scheduled` jobs, Elasticsearch synchronization, the
application health polling task, Flyway migrations, and model warm-up. It can validate startup,
configuration, dependency connectivity, health, and telemetry without taking
traffic or competing with the old production instance.

The candidate joins the existing `middleware-net` for MySQL, Redis, RabbitMQ,
and Elasticsearch, `nacos-net` for Nacos, and `aidiag-telemetry` for Alloy.
These names are the verified cloud networks; the deployment does not recreate
or publish the middleware services. Candidate environment overrides route
MySQL, Redis, RabbitMQ, and Elasticsearch through their Docker DNS names rather
than the public host address stored for the legacy deployment.

The release contains source code, not a locally built Jar. Docker runs the
tests and builds the Jar in a pinned Java 17/Maven image, then copies only the
application Jar and OpenTelemetry agent into a non-root Java 17 runtime image.

Cloud layout:

~~~text
/opt/ai-chat/releases/<release-name>  immutable source release
/opt/ai-chat/runtime/candidate.env    root-owned non-secret runtime settings
/opt/ai-chat/candidate                symlink to the active candidate release
~~~

Prepare runtime configuration once:

~~~bash
install -d -m 700 /opt/ai-chat/runtime
install -m 600 deploy/candidate.env.example /opt/ai-chat/runtime/candidate.env
~~~

Stage a release without changing Nginx or DNS:

~~~bash
bash deploy/cloud-stage-candidate.sh /opt/ai-chat/releases/<release-name>
~~~

Production activation is intentionally not implemented by this script. It
requires separate TLS, Nginx, credential-rotation, old-instance shutdown, and
DNS rollback gates.
