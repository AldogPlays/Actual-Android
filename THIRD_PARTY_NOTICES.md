# Third-party notices

The MIT license in LICENSE covers original Actual-Android wrapper code. It does not relicense bundled dependencies or Actual Budget's interface.

- **Mozilla GeckoView 158.0.20261007201254**: GeckoView and Gecko use the Mozilla Public License 2.0 and include components under additional third-party licenses. See [MPL 2.0](https://www.mozilla.org/en-US/MPL/2.0/), [Mozilla's source and licensing documentation](https://firefox-source-docs.mozilla.org/), and the [Firefox release source tree](https://github.com/mozilla-firefox/firefox/tree/release). The unmodified engine is obtained from [Mozilla Maven](https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/158.0.20261007201254/); its Java sources JAR is distributed alongside the AAR. Gecko's packaged license notices remain in the unmodified engine payload. Native-engine source and third-party codec licenses are part of the Mozilla source distribution; the Java source JAR is not the complete native engine source.
- **AndroidX and Media3**: transitive libraries used by GeckoView; [AndroidX licensing](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt), principally Apache License 2.0. Dependency artifacts retain their included notices.
- **Kotlin runtime and Gradle wrapper**: Apache License 2.0. See [Kotlin](https://github.com/JetBrains/kotlin) and [Gradle](https://github.com/gradle/gradle). Wrapper scripts preserve their original copyright/license headers.
- **Actual Budget**: separately hosted software, rendered by the embedded engine. See [Actual source and license](https://github.com/actualbudget/actual). It is not bundled in this APK. Screenshots illustrate Actual's web UI.

Actual-Android does not modify Gecko source, claim ownership of third-party code, or represent endorsement by Mozilla or Actual Budget. Engine/dependency updates require reviewing their notices and source availability again.
