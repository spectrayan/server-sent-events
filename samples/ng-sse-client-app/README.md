# Angular SSE Client Sample App

A minimal Angular application demonstrating how to consume Server‑Sent Events (SSE) using `@spectrayan-sse/ng-sse-client`.

## Prerequisites
- Node.js 20+
- npm
- A running SSE server (you can use `samples/sse-sample-server-app` from this repo)

## Install dependencies
From the repo root:
```
make setup
```

## Run the sample
Start the Spring Boot sample server (in a separate terminal):
```
mvn -pl samples/sse-sample-server-app spring-boot:run
```
This starts the SSE server on `http://localhost:8080`.

Start the Angular app:
```
npx nx serve ng-sse-client-app
```
Then open the URL shown in the terminal (typically `http://localhost:4200`).

## How it works
Open `samples/ng-sse-client-app/src/app/app.ts` to see the usage:
- It injects the `SseClient`
- Subscribes to `stream<any>(url, { events: ['message', 'notification'] })`
- Parses payloads with a custom `parse` function (fallbacks to raw string)

By default, when you select a user in the UI, the app connects to:
```
http://localhost:8080/{userId}
```
The server sample emits a periodic complex `notification` event and simple `message` strings.

## Customize endpoint
Change the `url` in `app.ts` if your server runs elsewhere (host/port/path).

## Build
```
npx nx build ng-sse-client-app
```

## Test
```
npx nx test ng-sse-client-app --ci --codeCoverage=false
```

## Support
Questions or issues: support@spectrayan.com
