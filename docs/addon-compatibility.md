# 既存アドオンの互換性・配布元確認

確認日: 2026-10-22

共通要件74の章分けでは、既存の4アドオン・15教材を扱います。新しいAE2アドオンの追加は今回の範囲に含めません。各章は同名MODの導入時だけ標準タグ一覧・コース・ME Guide本文と索引へ現れます。

| 章／MOD ID | 教材数 | NeoForge 1.21.1 | Forge 1.20.1 |
| --- | --- | --- | --- |
| AE2 OMNI Cells／`ae2omnicells` | 5 | 1.1.6 | 1.1.6 |
| Bigger AE2／`bigger_ae2` | 4 | 1.4.9 | 20.2.1 |
| AE2 Things／`ae2things` | 3 | 1.4.2-beta | 1.2.1 |
| AE2 MEGA Things／`ae2_mega_things` | 3 | 2.0.4 | 1.0.8 |

すべて任意依存です。レッスンID・導入判定・模型は維持し、所属章をアドオンの実MOD IDへ分けます。MEGA Things自体が要求するThings・MEGA Cells・Kotlin for Forgeは通常のMOD依存として必要です。MEGA Cellsを単独の追加対応アドオンとして数えていません。

## 実ファイルと一致した配布元

Gradleキャッシュの実JARのSHA-1をModrinthの`version_file` APIへ照合し、返された版ID・対象Minecraft・ローダーを確認しました。Things／MEGA Thingsの4配布物は同APIに存在せず、実在するCurseForgeのファイルを確認しました。対応するNeoForge版については、指定Prismの既存`.index`の配布元・ダウンロードハッシュも実JARと一致しています。

| MOD | NeoForge配布元 | Forge配布元 |
| --- | --- | --- |
| AE2 OMNI Cells | [Modrinth `RYE1pYyr`／`k444ySGT`](https://modrinth.com/mod/ae2-omni-cells/version/k444ySGT) | [同／`c2s3iMw8`](https://modrinth.com/mod/ae2-omni-cells/version/c2s3iMw8) |
| Bigger AE2 | [Modrinth `tHmKhHkk`／`xoGFs4oP`](https://modrinth.com/mod/bigger-ae2/version/xoGFs4oP) | [同／`KwLq5PZr`](https://modrinth.com/mod/bigger-ae2/version/KwLq5PZr) |
| AE2 Things | [CurseForge `609977`／`5637783`](https://www.curseforge.com/minecraft/mc-mods/ae2-things-forge/files/5637783) | [同／`4616683`](https://www.curseforge.com/minecraft/mc-mods/ae2-things-forge/files/4616683) |
| AE2 MEGA Things | [CurseForge `1150075`／`6203833`](https://www.curseforge.com/minecraft/mc-mods/ae2-mega-things/files/6203833) | [同／`6203824`](https://www.curseforge.com/minecraft/mc-mods/ae2-mega-things/files/6203824) |
| MEGA Cells（依存） | [Modrinth `jjuIRIVr`／`RPG4EriK`](https://modrinth.com/mod/mega/version/RPG4EriK) | [同／`SH2D1n3s`](https://modrinth.com/mod/mega/version/SH2D1n3s) |
| Kotlin for Forge（依存） | [Modrinth `ordsPcFz`／`uhJhCT7X`](https://modrinth.com/mod/kotlin-for-forge/version/uhJhCT7X) | [同／`Zsh14XeQ`](https://modrinth.com/mod/kotlin-for-forge/version/Zsh14XeQ) |
| Cloth Config（Forge側の依存） | この構成では不要 | [Modrinth `9s6osm5g`／`t8TXrZvZ`](https://modrinth.com/mod/cloth-config/version/t8TXrZvZ) |

依存の検証版はNeoForge側でMEGA Cells 4.11.0・Kotlin for Forge 5.12.0、Forge側でMEGA Cells 2.4.6-1.20.1・Kotlin for Forge 4.12.0・Cloth Config 11.1.136です。Bigger AE2のForge版20.2.1は、他の枝のバージョン名から推定せず、この実ファイルのハッシュと配布情報でForge 1.20.1対応を確認しています。

## 必要依存と再利用

- OMNI Cells: NeoForge版はAE2 19.2.2-beta以上、Forge版はAE2 15.0.10以上を要求します。
- Bigger AE2: NeoForge版はAE2 19.0.26以上、Forge版はAE2 15.0.0以上を要求します。
- Things: NeoForge版はAE2 19.0.0-beta以上20未満、Forge版はAE2 15.0.0以上16未満を要求します。
- MEGA Things: AE2 Things 1.2以上、MEGA Cells 2.1以上、AE2、Kotlin for Forgeを要求します。Kotlin for Forgeの下限はNeoForge版5.6.0、Forge版4.11.0です。
- MEGA Cells: NeoForge版はAE2 19.2.15以上、Forge版はAE2 15.1.0以上16未満とCloth Configを要求します。

指定Prism環境のAE2 19.2.18／NeoForge 21.1.262はこれらの条件を満たし、4アドオン・MEGA Cells・Kotlin for Forgeの既存JARも開発用キャッシュの実ファイルと一致しています。今回、第三者JARのダウンロード・追加・更新や`.index`の置換は不要です。調査時は親の開始基準34ファイル・22件の`.index`と全ハッシュが一致しました。最終配置では、他担当の正当な変更もあり得るため、配置直前の状態から他ファイルの不変を改めて確認します。

Kotlin for Forge 5.12.0の`all.jar`は、外側が`FMLModType: LIBRARY`で、`kotlinforforge`のMODメタデータは内包する`kffmod` JARにあります。最上位の`mods.toml`だけを探して未導入と判断せず、検証済み外側JARのハッシュと`jarjar`内の実メタデータを照合します。

## 権利表示

独立した導入済みJARを再利用し、アセットやコードをガイドへ取り込みません。実JARではOMNI CellsがLGPL-3.0-or-later（Modrinth表示はLGPL-3.0-only）、Bigger AE2とThingsがMIT、MEGA CellsがLGPLv3、Kotlin for ForgeがLGPL-2.1-only、Cloth ConfigがGNU LGPLv3とされています。MEGA Thingsは[公式リポジトリ](https://github.com/Lapis256/AE2MEGAThings)でコードLGPL-3.0と派生テクスチャCC BY-NC-SA 3.0を分けています。依存を配布・改変する場合は、各実配布物の権利表示と条件を改めて確認してください。
