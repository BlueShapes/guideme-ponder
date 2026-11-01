# Licensing and third-party notices

AE2 Ponder Guide is maintained and published by **aosankaku**:
https://github.com/AoSankaku/ae2-ponder-guide

Original project code and guide content are available under the MIT License in
`LICENSE`, Copyright (c) 2026 aosankaku and contributors, to the extent copyright
applies. The third-party portions identified below retain their original terms.
AI-assisted generation does not replace those terms or the original attribution.

This notice, `LICENSE`, and all files in `LICENSES/` are included in the mod JAR.
The dependency MODs and their embedded libraries are distributed separately;
their own license terms continue to apply.

## Ponder: adapted code and UI text (MIT)

- Original project: Ponder, by The Create Team.
- Copyright (c) 2022 The Create Team.
- Source: https://github.com/Creators-of-Create/Ponder
- Versions used: 1.0.87+mc1.21.1 (NeoForge) and 1.0.87 (Forge).
- License: MIT; the original notice and full terms are in
  `LICENSES/MIT-Ponder.txt`.

Covered portions:

- `src/main/java/dev/ae2guide/mixin/PonderTagScreenMixin.java` contains code
  adapted from Ponder's `PonderTagScreen`. This project changes the layout and
  adds responsive item pagination.
- The `ponder.*`, `key.ponder.ponder`, and `key.categories.ponder` UI entries in
  `src/main/templates/lang/en_us.json` and `ja_jp.json` reproduce upstream UI text.
  Corresponding entries in `src/main/translations/*.json` translate or adapt
  that text. These entries are generated into `assets/ponder/lang/*.json`.

Original screen source:
https://github.com/Creators-of-Create/Ponder/blob/mc1.21.1/dev/common/src/main/java/net/createmod/ponder/foundation/ui/PonderTagScreen.java

Ponder itself is loaded as a separate dependency and is not bundled in this JAR.

## GuideME: UI text and translations (CC BY 4.0)

- Original project: GuideME, by shartte and the GuideME contributors
  (AppliedEnergistics).
- Source: https://github.com/AppliedEnergistics/GuideME
- Version used: 21.1.19.
- Original resource: `assets/guideme/lang/en_us.json` in that release's JAR.
- Upstream asset license declaration:
  https://www.curseforge.com/minecraft/mc-mods/guideme/license
- License: Creative Commons Attribution 4.0 International (CC BY 4.0).
  https://creativecommons.org/licenses/by/4.0/
- Full terms: `LICENSES/CC-BY-4.0.txt`.

The 36 `guideme.*`, `item.guideme.*`, and `key.guideme.*` entries in
`src/main/templates/lang/en_us.json` reproduce GuideME's English UI strings.
Corresponding entries in `src/main/templates/lang/ja_jp.json` and
`src/main/translations/*.json` are translations or adaptations by this project.
They are generated into `assets/guideme/lang/*.json`. These GuideME-derived UI
resources, including this project's translated contributions, are distributed
under CC BY 4.0 rather than the project's default MIT license.

Changes: additional locale translations and adaptations to UI wording. Original
resource provenance and this notice were recorded on 2026-10-13. No endorsement
by GuideME or AppliedEnergistics is implied.

## GuideME: separately installed library (LGPLv3)

GuideME's mod code is licensed under GNU LGPL version 3:
https://guideme.appliedenergistics.org/license/

The library and its use are covered by that license. A copy of the LGPL and the
GPL version 3 incorporated by it is provided in `LICENSES/LGPL-3.0.txt` and
`LICENSES/GPL-3.0.txt`.

Source for the dependency version:
https://github.com/AppliedEnergistics/GuideME/tree/v21.1.19
https://github.com/AppliedEnergistics/GuideME/tree/v20.1.15 (Forge 1.20.1)

This mod uses GuideME's interfaces and supplies its own integration Mixins.
GuideME classes and its embedded libraries are not copied into this JAR. The
user installs GuideME separately, and may replace it with an interface-compatible
modified version. This project's license imposes no restriction on modifying
GuideME or reverse engineering to debug those modifications.

## Gradle Wrapper: build tooling (Apache-2.0)

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are Gradle build
tooling, rather than AI-generated project code. Their original copyright and
license notices are retained in the scripts and the Wrapper JAR.

- Project: https://github.com/gradle/gradle
- License: Apache License, Version 2.0.
- Full terms: `LICENSES/Apache-2.0.txt` (preserved from the Wrapper JAR).

The Wrapper is included in the source repository, not in the distributed mod JAR.

## Other separately installed dependencies

Applied Energistics 2, NeoForge, Forge, Flywheel, supported AE2 add-ons and their required
dependencies retain their own licenses. This project refers to their registry
identifiers and uses their runtime APIs/resources; it does not bundle their MOD
JARs, textures, models, sounds, or implementations. Runtime use of Minecraft's
ICU text API likewise does not bundle an ICU implementation in this mod.
