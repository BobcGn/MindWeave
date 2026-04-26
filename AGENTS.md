# AGENTS.md

## Project Summary

MindWeave is a Kotlin Multiplatform personal journaling and scheduling app with AI-assisted workflows.

Core product constraints from `docs/spec.md` and `docs/Ruler.md`:

- local-first / offline-capable behavior
- device-local SQLite is the source of truth for user data
- sync happens above the database through server mediation
- AI integrations stay behind shared abstractions

## Main Modules

- `composeApp`: Compose Multiplatform UI for Android and desktop, plus shared client UI code.
- `shared`: shared domain models, SQLDelight schema, repositories, sync logic, networking, AI abstractions, and app wiring.
- `server`: Ktor server for health checks, device registration, sync push/pull, and AI chat endpoints.
- `iosApp`: Xcode entry point for the iOS app.
- `harmonyApp`: DevEco / HarmonyOS host app. This is not part of the default Gradle settings file; use the dedicated OHOS profile files and helper script when working on it.

Default Gradle settings include only `:composeApp`, `:shared`, and `:server`.

## Source Layout

- package root: `org.example.mindweave`
- shared app graph: `shared/src/commonMain/kotlin/org/example/mindweave/app`
- local repositories: `shared/src/commonMain/kotlin/org/example/mindweave/data/local`
- sync stack: `shared/src/commonMain/kotlin/org/example/mindweave/sync`
- AI layer: `shared/src/commonMain/kotlin/org/example/mindweave/ai`
- SQLDelight schema and migrations: `shared/src/commonMain/sqldelight/org/example/mindweave/db`
- server tests: `server/src/test/kotlin/org/example/mindweave/server`

## Working Rules

- Preserve the local-first architecture. User writes must succeed against local storage without waiting on the network.
- Keep business logic in `shared` whenever it is domain, repository, sync, serialization, or AI-related. Platform modules should stay thin.
- Keep AI provider-specific code behind the existing abstraction layer. Do not couple UI code or repository code directly to a single provider SDK.
- When changing sync behavior, preserve the server-mediated multi-device flow and conflict handling model.
- When changing SQLDelight schema or queries, update the `.sq` files and add or adjust migrations.
- For HarmonyOS work, keep the bridge thin. Do not move business rules, sync logic, or schema management into Harmony bridge code.

## Validation

Use JDK 21.

Run the smallest relevant Gradle task for the area you changed:

- shared logic and repositories: `./gradlew :shared:jvmTest`
- server routes and services: `./gradlew :server:test`
- Android app build: `./gradlew :composeApp:assembleDebug`
- desktop app run: `./gradlew :composeApp:run`
- server dev run: `./gradlew :server:run`

For HarmonyOS:

- inspect the OHOS profile: `./gradlew -c settings.2.0.ohos.gradle.kts printHarmonyToolchain`
- publish native outputs into `harmonyApp`: `./2.0_ohos_mindweave_build.sh Debug`

Do not assume generic README template commands are still valid. Prefer the module build files and existing scripts over template documentation when they disagree.

## Data and API Notes

- The shared SQLDelight schema currently includes diary, schedule, tag, chat, profile, and sync tables.
- The server currently exposes `GET /health`, `POST /devices/register`, `POST /sync/push`, `POST /sync/pull`, and `POST /ai/chat`.
- Current server tests rely on the existing HTTP contract and in-memory sync behavior. Avoid changing the wire contract unless the task explicitly calls for it.

## Generated Files

Do not commit generated or tool-owned artifacts such as:

- `build/`, `.gradle/`, `.kotlin/`
- `harmonyApp/.hvigor/`, `harmonyApp/.appanalyzer/`, `harmonyApp/**/oh_modules/`
- `harmonyApp/entry/src/main/libs/**/libmindweave.so`
- `harmonyApp/entry/src/main/libs/**/mindweave_bridge_mode.txt`

## References

- `README.md` for general project entry points
- `docs/spec.md` for product scope
- `docs/Ruler.md` for architecture constraints
- `docs/mindweave-plan.md` for current implementation status
