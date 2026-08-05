# Code review — importer-service (runda 2, 2026-08-05)

> Runda 1 (2026-07-28) era un *plan de deploy*. Acum serviciul e migrat, are CI/CD propriu și e declarat în `ms-gitops`. Review-ul de față verifică ce a rămas din runda 1 și ce a apărut nou în commit-urile `d8463b7` → `d5b30d4`.

## Status runda 1

| # | Constatare runda 1 | Status |
|---|---|---|
| B2 | lipsă actuator | ✅ rezolvat — `pom.xml:102` |
| M2 | logback TCP spre Logstash | ✅ rezolvat — `logback-spring.xml` = JSON pe stdout |
| M3 | CI cu tag din dată | ✅ rezolvat, și bine făcut — `ci.yml` cu `sha7` + `cd-bump` în gitops |
| B1 | parolă/IP reale în git | ⚠️ **parțial** — user dedicat există (SealedSecret `external-db`), dar valorile reale au rămas ca *default* în cod (vezi B2 mai jos) |
| B3 | de unde citește importer-ul | ✅ decis — MySQL extern `78.96.25.131`, user read-only |
| M1 | full re-publish la 10 min | ⏳ deschis (vezi M6) |
| C1–C4 | cleanups | ⏳ nerezolvate |

**CI/CD-ul e partea cea mai bună a rundei ăsteia.** `ci.yml:57-67` — guard `test -f "$VALUES"` cu mesaj de eroare explicit, idempotență prin `git diff --quiet`, tag imutabil = SHA. Ăsta e exact pattern-ul corect de închidere a buclei CI→CD.

---

## Update — după commit-urile `a7521bf` → `7504887` (2026-08-05)

Ai reparat deja, corect:

- **B1 (producer Kafka)** ✅ — `spring.kafka.producer` cu `JsonSerializer` + `spring.json.add.type.headers: false`, pus în **ambele** fișiere (`application.yaml:104-108`, `application-argo.yaml:47-51`). N-ai căzut în capcana din M2: ai reparat în amândouă odată.
- **B2 (credențiale)** ✅ — `${MYSQL_USERNAME}` / `${MYSQL_PASSWORD}` fără default. Acum, dacă secret-ul lipsește, aplicația nu pornește și îți spune de ce, în loc să se conecteze tăcut cu root.
- **C6** ✅ — `System.out.println` comentat în bucla de sync.

Două lucruri de corectat:

### ⚠️ Corecție la C3 — recomandarea de imagine era greșită

**Recomandarea mea inițială (`eclipse-temurin:17-jre-alpine`) e greșită și e cauza celor 3 pipeline-uri roșii.** Variantele `-alpine` de Temurin sunt publicate **doar pentru `linux/amd64`**. CI-ul cere `platforms: linux/amd64,linux/arm64` (`ci.yml:38`), buildx caută manifestul arm64, nu-l găsește, și se oprește înainte de build:

```
ERROR: failed to solve: eclipse-temurin:17-jre-alpine:
  no match for platform in manifest: not found
```

Nu e diferența `jre` vs `jdk` — e sufixul `-alpine`. Imaginile fără sufix (bazate pe Ubuntu) au și `amd64`, și `arm64`:

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
EXPOSE 8082
COPY --from=build /app/target/importer-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Asta rezolvă și partea de multi-stage din C3 (Maven nu mai rămâne în imaginea finală).

**Lecția, care e mai valoroasă decât fix-ul:** „imagine mai mică" și „imagine multi-arch" sunt două cerințe care se pot bate cap în cap. Alpine e mic pentru că folosește musl în loc de glibc, iar asta înseamnă build-uri separate pe care nu toți furnizorii le publică pentru toate arhitecturile. Înainte să alegi un tag pentru un build multi-arch, verifică ce arhitecturi are:

```bash
docker buildx imagetools inspect eclipse-temurin:17-jre | grep -i platform
docker buildx imagetools inspect eclipse-temurin:17-jre-alpine | grep -i platform
```

Dacă vrei să tai și cele ~6 minute de build: cu multi-arch, Maven compilează de două ori, a doua oară sub emulare QEMU. Alternativa e să construiești jar-ul **o singură dată** în runner (ai deja `setup-java` în job-ul `build-test`), să-l urci ca artifact, iar Dockerfile-ul să facă doar `COPY app.jar` — atunci multi-arch înseamnă doar două layere de bază, nu două compilări.

### ⚠️ C5 luat invers

`config/CorsProperties.java:8` — ai schimbat `@ConfigurationProperties(prefix = "cors")` în `@ConfigurationProperties` fără prefix. Clasa rămâne moartă (CORS-ul real vine din `CorsConfig.java:15`, prin `@Value`), doar că acum se leagă la rădăcina configului în loc de un prefix inexistent. C5 cerea **ștergerea** clasei și a lui `@EnableConfigurationProperties` din `ImporterApplication.java:11`, nu ajustarea prefixului.

---

## 🔴 Critice

### B1 — Kafka producer fără serializer: niciun mesaj nu pleacă

`src/main/resources/application.yaml:102-113` și `src/main/resources/application-argo.yaml:45-56`

Blocul `spring.kafka` conține **doar `consumer:`**. Nu există `producer:` nicăieri, și nici un `@Bean ProducerFactory` în cod.

Spring Boot are default `spring.kafka.producer.value-serializer = StringSerializer`. Serviciul trimite un obiect:

`src/main/java/com/example/importer/service/MessagePublisherService.java:25`
```java
kafkaTemplate.send(topic, id, event);   // event = MessageEvent, nu String
```

→ la fiecare rulare a scheduler-ului (10 min):
```
org.apache.kafka.common.errors.SerializationException: Can't convert value of class
com.example.importer.model.MessageEvent to class org.apache.kafka.common.serialization.StringSerializer
```

**E o regresie**, nu o scăpare veche: configul vechi (acum comentat, `application.yaml:34-40`) avea `producer.value-serializer: JsonSerializer` + `spring.json.add.type.headers: false`. La rescrierea configului din `d8463b7` s-a păstrat blocul de *consumer* (care nu-i folosește la nimic) și s-a pierdut cel de *producer* (singurul de care serviciul chiar are nevoie).

**Mecanismul de dedesubt:** genericele din `KafkaTemplate<String, MessageEvent>` sunt șterse la compilare (type erasure). Kafka nu are de unde să deducă serializer-ul din tipul declarat — îl citește exclusiv din proprietatea `value.serializer`. Tipul din generics e doar o promisiune către compilator, nu o configurare de runtime.

### B2 — Parola scursă a rămas ca *default activ*, nu doar în istoric

`src/main/resources/application.yaml:90-92` și `src/main/resources/application-argo.yaml:33-35`
```yaml
url: ${MYSQL_URL:jdbc:mysql://78.96.25.131:3306/test_db}
username: ${MYSQL_USERNAME:root}
password: ${MYSQL_PASSWORD:R@0t}
```

Diferența față de runda 1 e importantă: atunci era o valoare fixă (greșeală vizibilă). Acum e un **fallback tăcut**. Dacă în cluster secret-ul `external-db` nu ajunge în namespace-ul `business` — reflector-ul nu a copiat încă, cineva redenumește cheia `READ_PASSWORD`, secret-ul e recreat gol — pod-ul **pornește normal**, `Healthy` în ArgoCD, și se conectează la baza reală cu `root`/`R@0t`. Nu ai niciun semnal că rulezi pe credențiale de root scurse public.

Un default într-un `${}` nu e documentație. E o decizie de runtime, luată exact atunci când ești mai puțin atent.

Legat: `business/rsk/importer-service/values.yaml:20` ține `MYSQL_URL` cu IP-ul în clar în gitops — acolo e ok (nu e secret), dar înseamnă că default-ul din cod nu servește la nimic nici măcar în cluster.

---

## Before / After (critice)

| # | Acum | Cum ar trebui |
|---|---|---|
| B1 | `spring.kafka:`<br>`  bootstrap-servers: ...`<br>`  consumer:` … (doar consumer) | `spring.kafka:`<br>`  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`<br>`  producer:`<br>`    key-serializer: org.apache.kafka.common.serialization.StringSerializer`<br>`    value-serializer: org.springframework.kafka.support.serializer.JsonSerializer`<br>`    properties:`<br>`      spring.json.add.type.headers: false` |
| B2 | `password: ${MYSQL_PASSWORD:R@0t}`<br>`username: ${MYSQL_USERNAME:root}`<br>`url: ${MYSQL_URL:jdbc:mysql://78.96.25.131:3306/test_db}` | `password: ${MYSQL_PASSWORD}`<br>`username: ${MYSQL_USERNAME}`<br>`url: ${MYSQL_URL}`<br>(fără default → dacă lipsește env-ul, aplicația **nu pornește** și scrie de ce; pentru local pui valorile în `.env` / run config, nu în git) |

`spring.json.add.type.headers: false` nu e opțional: data-service consumă cu `spring.json.use.type.headers: false` + `value.default.type` — dacă producer-ul trimite header de tip, consumer-ul îl ignoră, dar dacă îl trimite cu alt package (`com.example.importer.model.MessageEvent` vs `com.example.data_service.model.MessageEvent`) și cineva pune vreodată `use.type.headers: true`, deserializarea crapă. Contractul între cele două servicii e **JSON-ul**, nu clasa Java.

**Verify după fix:**
```bash
kubectl -n business logs deploy/importer-service | grep -i "serializ\|Triggering scheduled"
# apoi, in Kafka UI: product-topic -> messages (trebuie sa apara la max 10 min)
kubectl -n data exec -it moco-mysql-0 -- mysql -u... micro_db -e "SELECT COUNT(*) FROM ..."
```

---

## 🟡 Importante

**M1 — totul depinde de `SPRING_PROFILES_ACTIVE=argo`, tăcut.**
`application.yaml:85` are `expected-issuer: http://localhost/keycloak/realms/rsk` — **fără `${}`**, singura proprietate din tot fișierul care n-a fost făcută env-driven. În cluster e salvată de `application-argo.yaml:20`, care o suprascrie. Dacă cineva șterge din greșeală `SPRING_PROFILES_ACTIVE: argo` (`values.yaml:17`), serviciul pornește, trece de probe, apare `Healthy` — și respinge **orice** token cu `401 invalid_token`, pentru că validează issuer-ul contra unui `localhost` inexistent. Fă-o env-driven ca restul.

**M2 — `application.yaml` și `application-argo.yaml` sunt ~90% identice.**
Diferă real doar: `cors.allowed-origins`, `elk` hosts, blocul `keycloak`, și `jwk-set-uri` (env vs fix). Dovada că duplicarea costă: **B1 e prezent în ambele fișiere** — un singur fix nu ajunge, trebuie făcut de două ori, și exact așa apar diferențele accidentale între „merge local" și „merge în cluster". Runda 1, Pasul 2 cerea un singur fișier env-driven; `application-helm.yaml` și `application-docker.yaml` au fost șterse corect, dar apoi a apărut al doilea fișier la loc.

**M3 — config de consumer într-un serviciu care nu consumă nimic.**
`application.yaml:104-113` — `group-id`, `auto-offset-reset`, și `spring.json.value.default.type: com.example.data_service.model.MessageEvent`, o clasă care **nu există în acest proiect**. Copy-paste din data-service. Nu strică nimic (fără `@KafkaListener` nu se creează niciun consumer), dar e fix genul de config care te face să cauți bug-ul în locul greșit — cum s-a și întâmplat la B1: blocul arăta „plin de Kafka", deci părea configurat.

**M4 — bloc `keycloak:` mort, cu mină.**
`application-argo.yaml:23-28` — `keycloak.credentials.secret: ${KEYCLOAK_CLIENT_SECRET}`, fără default. Nicio clasă nu citește prefixul `keycloak.*` (nici `@ConfigurationProperties`, nici `@Value`), deci placeholder-ul nerezolvat nu deranjează pe nimeni azi. În ziua în care cineva scrie `@Value("${keycloak.credentials.secret}")`, aplicația nu mai pornește în cluster — `KEYCLOAK_CLIENT_SECRET` nu e în `values.yaml`. Un placeholder se rezolvă lazy, la citire, nu la pornire: de-asta minele astea explodează târziu. Șterge blocul (importer-ul e resource server, nu client confidențial — nu-i trebuie secret).

**M5 — trei repository-uri identice pe aceeași entitate.**
`repository/ImporterRepository.java`, `repository/StocOptimRepo.java`, `repository/TestRepository.java` — același `@Query` pe `MapStoc`, doar tipul de retur diferă. Folosit e doar primul (`service/MapStocOptImplService.java:20`). Celelalte două sunt beans create de Spring la fiecare pornire, pentru nimeni.

**M6 (rămasă din runda 1) — full re-publish la fiecare 10 minute.**
`scheduler/MapStocOptimImportScheduler.java:25-31` — toate rândurile, fiecare cu `UUID.randomUUID()` și acțiune `CREATED`. Întrebarea din runda 1 (Q2) e încă fără răspuns și acum e blocantă: după ce repari B1, mesajele chiar încep să curgă. Dacă data-service inserează naiv pe `CREATED`, `micro_db` crește cu un set complet de duplicate la fiecare 10 minute.

---

## 🟢 Cleanups

- **C1** — `config/SecurityConfig.java:1-99`: 99 de linii de config reactiv comentat, dintr-un alt serviciu (`package com.example.commandservice.config`). Din runda 1 s-au comentat doar `System.out.println`-urile (`:168-170`) în loc să se șteargă blocul.
- **C2** — `application.yaml:1-65`: 65 de linii de config comentat — cu parola `R@0t` în clar, a doua oară în același fișier.
- **C3** — `Dockerfile:1-16`: tot single-stage pe `openjdk:17.0.1-slim` (imagine deprecată), cu build Maven înăuntru. Combinat cu `ci.yml:38` (`platforms: linux/amd64,linux/arm64`) compilezi tot proiectul de două ori, a doua oară sub emulare QEMU → CI inutil de lent. Multi-stage: build o dată, `eclipse-temurin:17-jre-alpine` la runtime.
- **C4** — `ci.yml:42`: se împinge și `:latest`. În cluster nu se folosește (bine), deci e doar un tag care se mișcă sub tine când debughezi.
- **C5** — `config/CorsProperties.java:8`: `@ConfigurationProperties(prefix = "cors")`, dar în YAML proprietatea e `app.cors.allowed-origins` → bean-ul se creează gol și nu-l injectează nimeni. CORS-ul real vine din `CorsConfig.java:15` (`@Value`). Clasă moartă + `@EnableConfigurationProperties` inutil în `ImporterApplication.java:11`.
- **C6** — `controller/ImporterController.java:59`: `System.out.println(m.getArticol())` în bucla de sync — o linie non-JSON per articol, care sparge parsarea Filebeat exact pe volumul cel mai mare.
- **C7** — `controller/ImporterController.java:21`: `/api/v1/query` — path moștenit de la query-service.
- **C8** — `controller/ImporterController.java:16,18`: `StructuredArguments` importat de două ori (o dată `import`, o dată `import static`); mesajul `"Create message request received"` apare pe un `GET` care nu creează nimic.

---

## Q&A

**Q1.** `KafkaTemplate<String, MessageEvent>` — de unde știe Kafka că valoarea e `MessageEvent` și cum s-o transforme în bytes? Ce rol joacă genericele din declarație la runtime?

**Q2.** Dacă mâine secret-ul `external-db` nu ajunge în namespace-ul `business` (reflector nesincronizat, cheie redenumită): ce face pod-ul cu configul de acum, și ce ar face cu `${MYSQL_PASSWORD}` fără default? Care variantă e mai ușor de diagnosticat la 2 noaptea?

**Q3.** (rămasă din runda 1) Cum tratează data-service un eveniment `CREATED` cu `id` nou pentru un articol care deja există în `micro_db` — insert sau upsert? Dacă e insert, ce alegi: upsert în consumer, sau scheduler care publică doar delta?
