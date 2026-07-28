# Plan de deploy — importer-service (review + soluții)

> Completează [Ghid 2 — Migrare importer-service](../constantin-gitops/docs/migrare/02-importer-service.md) cu starea reală din 2026-07-28.
> Context nou față de ghid: **data-service e LIVE** pe platformă (chart `microservice` + values), realm-ul `rsk` e **updatabil din git** (keycloak-config-cli), topic-ul `product-topic` e declarat în `infra/kafka/topics/`.

## Sumar diagnostic

| # | Sev | Fișier | Problemă | Soluție |
|---|---|---|---|---|
| B1 | 🔴 | `application*.yaml` (toate 3) | `root`/`R@0t` pe `78.96.25.131` comis în git | env-driven + user dedicat + **rotire parolă** |
| B2 | 🔴 | `pom.xml` | **lipsă `spring-boot-starter-actuator`** → `/actuator/health` = 404 → probele omoară pod-ul | +1 dependență |
| B3 | 🔴 | arhitectură | de unde citește importer-ul în cluster? (sursa externă vs micro_db) | decizie — vezi Q1 |
| M1 | 🟡 | `MapStocOptimImportScheduler` | full re-publish la 10 min cu UUID nou/eveniment | verifică upsert în data-service — Q2 |
| M2 | 🟡 | `logback-spring.xml` | appender TCP spre Logstash inexistent + consolă non-JSON | JSON pe stdout, scoate TCP |
| M3 | 🟡 | `.github/workflows/deploy.yml` | tag din dată + `latest`; nume workflow copy-paste („Query-service") | tag = git SHA, ca data-service |
| C1 | 🟢 | `SecurityConfig.java` | ~100 linii cod reactiv comentat + `System.out.println("KSET=...")` | șterge |
| C2 | 🟢 | `application-helm.yaml` | copie identică a `application.yaml`, nu externalizează nimic | devine profilul de cluster sau dispare |
| C3 | 🟢 | `Dockerfile` | `openjdk:17.0.1-slim` (deprecated), single-stage (~600MB), jar hardcodat | multi-stage cu JRE |
| C4 | 🟢 | `ImporterController` | path `/api/v1/query` — nume moștenit de la alt serviciu | redenumire când atingi UI-ul |

Vestea bună: datorită Spring relaxed binding, serviciul e deployabil **fără modificări de cod** în afara B2 (actuator) și M2 (logback) — restul se rezolvă prin env în values. Dar B1 (rotirea parolei) rămâne obligatorie indiferent.

---

## Pași, în ordine (cu soluții gata de copiat)

### Pasul 1 — pom.xml: actuator (B2) `P0`

Fără asta, chart-ul cu `probes.path: /actuator/health` îți restartează pod-ul la infinit.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Verify local:** `./mvnw spring-boot:run` → `curl localhost:8082/actuator/health` → `{"status":"UP"}`.

### Pasul 2 — config env-driven (B1) `P0`

`application.yaml` — înlocuiește valorile fixe (păstrezi defaults DOAR pentru local, fără IP-uri/parole reale):

```yaml
spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/test_db}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root}
  kafka:                                  # k mic — capcana "Kafka:" merge, dar deruteaza
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://localhost:8080/realms/rsk/protocol/openid-connect/certs}
app:
  security:
    expected-issuer: ${KEYCLOAK_ISSUER:http://localhost:8080/realms/rsk}
  kafka:
    topic: ${APP_KAFKA_TOPIC:product-topic}
```

Șterge `application-helm.yaml` și `application-docker.yaml` dacă nu mai diferă (C2) — un singur fișier, env decide.

### Pasul 3 — rotirea parolei scurse (B1) `P0`

`R@0t` e în istoricul git → considerat public. Pe serverul MySQL sursă:

```sql
CREATE USER 'importer'@'%' IDENTIFIED BY '<parola-noua-generata>';
GRANT SELECT ON test_db.* TO 'importer'@'%';
ALTER USER 'root'@'%' IDENTIFIED BY '<alta-parola-root>';
```

Importer-ul citește doar (`findAll`, `findByIdArticol`) → `SELECT` e suficient. Dar atenție: `ddl-auto: update` cere DDL — pune `ddl-auto: none` în cluster (schema există deja) sau dă și ALTER/CREATE dacă vrei să rămână update.

### Pasul 4 — logback JSON pe stdout (M2) `P0`

`logback-spring.xml` complet înlocuit (platforma folosește Filebeat care citește stdout; Logstash nu există):

```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
  </appender>
  <root level="INFO">
    <appender-ref ref="CONSOLE" />
  </root>
</configuration>
```

Scoate din config `app.elk.*` (nu mai e folosit de nimic).

### Pasul 5 — CI cu tag = git SHA (M3) `P1`

În `.github/workflows/deploy.yml`:

```yaml
name: CD - Deploy importer-service
...
      - name: Set build number
        id: build-number
        run: echo "BUILD_NUMBER=$(git rev-parse --short HEAD)" >> $GITHUB_ENV
```

(rulează după checkout, nu înainte!). Tag-ul SHA e imutabil și trasabil — exact ca `06e667d` la data-service. `latest` nu se folosește în cluster.

### Pasul 6 — GitOps: SealedSecret + values + Application `P0`

Pe pattern-ul REAL folosit de data-service (chart `microservice` + multi-source `$values`), nu Deployment de mână.

**6a. SealedSecret** (o dată, din repo-ul ms-gitops):

```bash
kubectl create secret generic importer-db -n business \
  --from-literal=username='importer' \
  --from-literal=password='<parola-noua>' \
  --dry-run=client -o yaml \
| kubeseal --format yaml > business/rsk/importer-service/secrets/importer-db-sealed.yaml
```

**6b. `business/rsk/importer-service/values.yaml`:**

```yaml
replicas: 1

image:
  repository: ion21/import-service
  tag: <SHA-ul din CI>
  pullPolicy: IfNotPresent

containerPort: 8082

podAnnotations:
  co.elastic.logs/json.keys_under_root: "true"
  co.elastic.logs/json.add_error_key: "true"
  co.elastic.logs/json.message_key: "message"
  co.elastic.logs/json.overwrite_keys: "true"

env:
  KAFKA_BOOTSTRAP_SERVERS: demo-kafka-bootstrap.messaging.svc:9092
  APP_KAFKA_TOPIC: product-topic
  MYSQL_URL: "jdbc:mysql://<HOST-SURSA>:3306/test_db"   # vezi Q1
  KEYCLOAK_ISSUER: https://auth.icode.mywire.org/realms/rsk
  KEYCLOAK_JWK_SET_URI: https://auth.icode.mywire.org/realms/rsk/protocol/openid-connect/certs

secretEnv:
  MYSQL_USERNAME: { secret: importer-db, key: username }
  MYSQL_PASSWORD: { secret: importer-db, key: password }

probes:
  path: /actuator/health

resources:
  requests:
    cpu: 20m
    memory: 128Mi
  limits:
    memory: 512Mi

service:
  port: 8082

ingress:
  enabled: false          # intern: scheduler-driven, nimeni nu-l apeleaza din afara
```

**6c. `argo-apps/app-importer-service.yaml`:**

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: importer-service
  namespace: argocd
  annotations:
    argocd.argoproj.io/sync-wave: "5"
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: default
  sources:
    - repoURL: https://github.com/nimigeanconstantinion/ms-gitops.git
      targetRevision: master
      path: business/charts/microservice
      helm:
        releaseName: importer-service
        valueFiles:
          - $values/business/rsk/importer-service/values.yaml
    - repoURL: https://github.com/nimigeanconstantinion/ms-gitops.git
      targetRevision: master
      ref: values
    - repoURL: https://github.com/nimigeanconstantinion/ms-gitops.git
      targetRevision: master
      path: business/rsk/importer-service/secrets
  destination:
    server: https://kubernetes.default.svc
    namespace: business
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

### Pasul 7 — verify lanțul complet `P0`

```bash
# 0. ÎNAINTE de deploy: sursa e accesibilă din cluster?
kubectl -n business run nettest --rm -it --image=busybox --restart=Never -- nc -zv <HOST-SURSA> 3306

# 1. pod-ul
kubectl -n business get pod -l app=importer-service     # 1/1 Running, fara restarts

# 2. scheduler-ul lucreaza (la max 10 min)
kubectl -n business logs deploy/importer-service | grep "Triggering scheduled"

# 3. mesaje pe topic
#    Kafka UI -> product-topic -> messages (sau kafka-console-consumer)

# 4. data-service consuma -> randuri in micro_db
kubectl -n data exec -it moco-mysql-0 -- mysql -u... micro_db -e "SELECT COUNT(*) FROM ..."
```

## Definition of Done

- [ ] actuator în pom, `/actuator/health` = UP
- [ ] config env-driven, zero IP-uri/parole în cod
- [ ] parola `R@0t` rotită, user `importer` read-only
- [ ] logback JSON stdout, fără appender Logstash
- [ ] CI cu tag SHA, imagine în Docker Hub
- [ ] `nc -zv` din cluster spre sursa MySQL = OK
- [ ] values + SealedSecret + Application comise, ArgoCD Synced+Healthy
- [ ] mesaje vizibile pe `product-topic` + rânduri în `micro_db`

---

## Q&A (răspunde înainte de pasul 6)

**Q1 — Unde e sursa de date în producție?** `78.96.25.131:3306/test_db` e un MySQL extern (ERP?). În cluster, importer-ul are nevoie de acces la el. Variante: (a) rămâne extern → verifici reachability + user read-only; (b) sursa devine o tabelă în MOCO (`micro_db` sau alt schema) → cine o populează? Ghidul 2 sugerează alinierea pe `micro_db`, dar atunci importer-ul ar citi din aceeași bază în care scrie data-service — clarifică fluxul REAL de date înainte de a alege.

**Q2 — data-service face upsert sau insert la consum?** Scheduler-ul publică TOATE rândurile la fiecare 10 min, fiecare ca eveniment `CREATED` cu UUID nou. Dacă consumer-ul inserează naiv, `micro_db` crește nelimitat cu duplicate. Verifică handler-ul de `CREATED` din data-service; dacă nu e upsert pe cheia articolului, ori îl faci upsert, ori scheduler-ul publică doar delta.

**Q3 — cine apelează API-ul importer-ului?** Dacă nimeni (doar scheduler-ul intern) — rămâne `ingress.enabled: false` și nu-i trebuie client Keycloak dedicat. Dacă UI-ul va chema `/api/v1/query/sync` manual — atunci discutăm expunerea prin gateway (oauth2-proxy) la Ghid 3.
