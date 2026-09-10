# Spectrayan Kotlin SSE Client (`spectrayan-sse-kotlin`)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-minSdk%2021+-green.svg)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021-orange.svg)](https://adoptium.net)

Idiomatic, high-performance Kotlin client for consuming Server-Sent Events (SSE / W3C EventSource) on **Android**, **Compose Multiplatform**, and **JVM Kotlin** (Ktor, Spring Boot, Quarkus). Built on native Kotlin Coroutines `Flow<ServerSentEvent>`, automatic reconnection with randomized exponential jitter, and `Last-Event-ID` session resumption.

Part of the [Spectrayan Server-Sent Events](https://github.com/spectrayan/server-sent-events) polyglot client ecosystem.

---

## Features

- **Native Coroutines Flow**: Stream real-time events as a cold `Flow<ServerSentEvent>`, providing seamless integration with Kotlin Coroutines, Jetpack Compose, and Android Architecture Components.
- **Lifecycle & Scope Awareness**: Cancelling the collecting Coroutine scope cleanly aborts HTTP connections without memory leaks or orphan threads.
- **W3C Standard Compliance**: Full EventSource framing parser supporting multiline `data:` concatenation, custom `event:` types, `id:` tracking, `retry:` delays, and `:keepalive` comment filtering.
- **Resilient Reconnection**: Exponential backoff with full randomized jitter to prevent thundering herds on network reconnections:
  $$\text{delay} = \min(\text{initialDelay} \times \text{multiplier}^{\text{attempt}}, \text{maxDelay}) \times (1 \pm \text{jitter})$$
- **Stateful Resumption**: Automatically transmits `Last-Event-ID` on reconnect to recover missed events.
- **Pure Kotlin/JVM**: Standard Kotlin library runnable on any Android device (API 21+) and backend JVM environment without requiring Android SDK build dependencies.

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.spectrayan.sse:spectrayan-sse-kotlin:2.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.spectrayan.sse:spectrayan-sse-kotlin:2.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.spectrayan.sse</groupId>
    <artifactId>spectrayan-sse-kotlin</artifactId>
    <version>2.0.0</version>
</dependency>
```

---

## Quick Start

```kotlin
import com.spectrayan.sse.client.SpectrayanSseClient
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main() = runBlocking {
    val client = SpectrayanSseClient("https://api.example.com/sse/ticks") {
        bearerAuth("my-api-token")
        reconnection {
            initialDelay = 1.seconds
            maxDelay = 15.seconds
            jitter = 0.2 // ±20%
        }
    }

    client.stream()
        .collect { event ->
            println("[${event.event}] ID: ${event.id} -> ${event.data}")
        }
}
```

---

## Android & Jetpack Compose Integration

### In an Android ViewModel

```kotlin
class StockTickerViewModel(
    private val sseClient: SpectrayanSseClient
) : ViewModel() {

    private val _stockPrice = MutableStateFlow<Double?>(null)
    val stockPrice: StateFlow<Double?> = _stockPrice.asStateFlow()

    init {
        viewModelScope.launch {
            sseClient.streamEvents("price_update")
                .catch { err -> Log.w("SSE", "Connection warning: $err") }
                .collect { event ->
                    val newPrice = event.data.toDoubleOrNull()
                    _stockPrice.value = newPrice
                }
        }
    }
}
```

### In a Jetpack Compose UI

```kotlin
@Composable
fun StockTickerScreen(viewModel: StockTickerViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val price by viewModel.stockPrice.collectAsStateWithLifecycle()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Live Stock Ticker", style = MaterialTheme.typography.headlineMedium)
        Text(text = price?.let { "$$it" } ?: "Connecting...", style = MaterialTheme.typography.bodyLarge)
    }
}
```

---

## Event Filtering

To subscribe strictly to a specific event name:

```kotlin
client.streamEvents("order_completed")
    .collect { orderEvent ->
        val order = orderEvent.decode { jsonString ->
            // Use your preferred JSON library (kotlinx.serialization / Moshi / Jackson)
            MyJsonParser.parse(jsonString)
        }
        processOrder(order)
    }
```

---

## Configuration Options

| Option | Type | Default | Description |
|:---|:---|:---|:---|
| `header(name, value)` | `String, String` | None | Set custom HTTP header |
| `bearerAuth(token)` | `String` | None | Convenience helper for `Authorization: Bearer <token>` |
| `lastEventId(id)` | `String?` | `null` | Pre-seed `Last-Event-ID` to resume an earlier stream session |
| `reconnection { ... }` | DSL block | Defaults | Configure exponential backoff and jitter limits |
| `client(okHttpClient)` | `OkHttpClient` | Custom client | Provide customized OkHttpClient instance |
| `bufferCapacity(int)` | `Int` | `128` | Buffer capacity for flow emission |

### Reconnection Settings (`ReconnectionConfig`)

```kotlin
reconnection {
    initialDelay = 1.seconds   // Base delay for first reconnect
    maxDelay = 30.seconds      // Maximum backoff duration
    multiplier = 2.0           // Exponential growth factor
    jitter = 0.2               // ±20% randomized jitter
    maxRetries = 0             // 0 = unlimited retries
}
```

---

## Running Tests

```bash
./gradlew test
```

---

## License

Apache License 2.0. See [LICENSE](../../LICENSE) for details.
