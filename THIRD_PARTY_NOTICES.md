# Third-party notices

Inku's extension runtime includes compatibility interfaces and small host
bridges so user-installed Tachiyomi/Mihon manga extensions and Aniyomi anime
extensions can be loaded by Android's package/class-loader mechanisms.

The compatibility surface is intentionally limited to the API shapes required by
the inspected extensions. It is not a vendored copy of Mihon, Aniyomi, Tachiyomi
or their extension repositories.

## Tachiyomi/Mihon extension API compatibility

Files under `app/src/main/java/eu/kanade/tachiyomi/source`,
`app/src/main/java/eu/kanade/tachiyomi/source/model`,
`app/src/main/java/eu/kanade/tachiyomi/source/online`,
`app/src/main/java/eu/kanade/tachiyomi/network`, and
`app/src/main/java/eu/kanade/tachiyomi/util` provide a compatibility API for
extensions compiled against Tachiyomi/Mihon-style manga host contracts.

Reference projects:

- Mihon: https://github.com/mihonapp/mihon
- Tachiyomi extensions lineage: https://github.com/tachiyomiorg/extensions and compatible community forks

License references:

- Mihon is distributed under Apache License 2.0.
- Tachiyomi extension repositories commonly preserve the Apache License 2.0
  notice and Javier Tomas copyright lineage.

## Aniyomi extension API compatibility

Files under `app/src/main/java/eu/kanade/tachiyomi/animesource` provide a
compatibility API for extensions compiled against Aniyomi-style anime host
contracts.

Reference projects:

- Aniyomi: https://github.com/aniyomiorg/aniyomi
- Aniyomi extensions: https://github.com/aniyomiorg/aniyomi-extensions

License references:

- Aniyomi is distributed under Apache License 2.0.
- Aniyomi extensions preserve the Apache License 2.0 notice and Tachiyomi
  copyright lineage where applicable.

## Injekt compatibility namespace

Files under `app/src/main/java/uy/kohesive/injekt` provide a minimal local
compatibility namespace for extensions that request host dependencies through
Injekt-style APIs. This is a small Inku implementation of the expected API
surface, not a vendored copy of the upstream library.

Reference project:

- Injekt: https://github.com/kohesive/injekt

License reference:

- Published Injekt artifacts are listed as MIT licensed.
