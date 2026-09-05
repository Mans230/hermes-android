# Validation — 2026-09-05 · revision 0.2.0

| Check | Result |
|---|---|
| Compile and run Android-independent endpoint, SSE and mention router code | PASS — 26 assertions |
| Java syntax parser, all 11 source files | PASS — syntax only, not Android type checking |
| Parse 3 Android manifest/resource XML files | PASS |
| Diagnostic shell `bash -n` and read-only Python diagnostic syntax | PASS |
| GitHub Actions YAML parse | PASS |
| Reference-matched design PNG | PASS — 1280 × 980, manually inspected |
| Android Gradle build, Android Lint and JUnit suite | PASS — GitHub Actions run 33980375030; 9 JUnit tests, lint 0 errors / 3 warnings, debug APK assembled |
| Emulator or physical Android device | NOT RUN |
| Real Debian/Hermes integration, group turns, photo picker and Telegram coexistence | NOT RUN — server connection unavailable |
| Android encrypted storage and notification lifecycle | NOT RUN — requires Android runtime |

The 26 executed assertions cover SSE framing, partial events, UTF-8 BOM, keep-alive comments, multiline data, termination, event limits, HTTPS URL validation, recipient deduplication, case-insensitive handles, explicit broadcasts, code/email exclusions, unknown-mention rejection and handle validation.

The design uses example bots/messages and avatar crops from the user's supplied demo to show the requested visual layout. It is an authored concept illustration, not an emulator screenshot. The reference avatar crops are used only under `design/`, not shipped as live app bot identities.

The user reported Hermes v0.21.0, upstream 9dd6634c, on Debian. This is not evidence that API Server is enabled or reachable. No direct server changes or API calls to the user's bots were performed. Source was published to Mans230/hermes-android PR #1. Debug APK built from commit 7f9fb2eeca61967401433ef6be94719cf6fa79cc. APK SHA-256: e267129b4d7d741bce23787ce78072ba202c4dba331ae5fceadced8c2ce168ae.

Remaining lint warnings: target SDK 35 is not the latest; explicit Android 12 data extraction rules are absent; one concatenated display string. Android runtime and live integration remain untested.
