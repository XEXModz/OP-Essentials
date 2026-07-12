# Building OP-Essentials

## Current reality (read this first)

The original gradle project for this fork was lost to history — everything from
1.1.6 onward has been maintained by **surgical patching**, and it works great:

1. The reference source in `src/` is the decompiled (Vineflower) full tree,
   kept in sync with every patch we ship. It compiles as reference but the
   canonical artifact is the live jar.
2. To change something:
   - edit the class(es) in `src/main/java/`
   - compile **only those classes** against: the mojmap Minecraft server jar
     (must be FIRST on the classpath) + `neoforge-21.1.234-universal.jar`
     + the server's `libraries/*.jar` + the current release jar
   - swap the compiled `.class` files into a copy of the current jar
   - bump `version=` in `META-INF/neoforge.mods.toml`, rename, ship
3. Verify reflection targets with `javap` against the live jar before calling
   them from other mods.

## Why not gradle?

Because the decompiled tree has never needed a full rebuild — surgical patches are
faster, diffable (`patches/`), and can't introduce regressions in untouched classes.
A proper gradle re-bootstrap is planned alongside the package rename for v1.0.

## Deploy discipline

- Never swap the jar with players online — empty server + explicit go
- Keep the previous jar as `.bak` next to it for instant rollback
- Watch the boot log: mod version line + zero new ERRORs = good deploy
