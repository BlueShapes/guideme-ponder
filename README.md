# AE2 Ponder Guide

Minecraft 1.21.1 / NeoForgeと1.20.1 / Forge向けの、Applied Energistics 2を立体アニメーションで学ぶガイドです。
独立版Ponderを使用するため、**Create本体は不要**です。22言語に対応しています。
AE2本体は6章・41レッスン・205ステップで、内容に合ったアイテムから関連場面を開けます。
装飾ブロック・ファサード・ケーブルアンカーに加え、工具・材料・セル・任意アドオンにも短いアイテム説明があります。短文は70定義で、内容に応じてPonderと併用します。5系統×17色のケーブルは同系統の共通コースへ接続し、チャージ済みのスタッフには専用教材があります。対象版ごとの全アイテムと検証範囲は[入口の監査記録](docs/item-coverage.md)を参照してください。
対応アドオンには計15レッスン・75ステップを追加し、導入済みのものを表示します。
MEガイドのアイテムからは、利用可能な全レッスンのコースを開けます。

## ダウンロード・導入

[GitHub Releases](https://github.com/AoSankaku/ae2-ponder-guide/releases) からJARをダウンロードできます。
使用するMinecraft・ローダーに合うJARを、下記の「必要なMOD」と一緒に `mods` フォルダへ配置し、
ゲームを再起動してください。1.20.1 / Forge用JARには `forge-1.20.1` が付きます。
対応アドオンは任意です。Create本体の導入は不要です。

## 使い方

1. インベントリで対応するAE2アイテムにカーソルを合わせ、**Wキーを長押し**するとPonderが開きます。
2. Ponderの再生、一時停止、タイムライン、キーフレーム、ブロック識別を利用できます。
3. Ponder左上の標準タグボタンから、現在の教材・アイテムのカテゴリーと、そのカテゴリーの他の教材へ進めます。
4. ME Guideのサイドバー「Ponder」にも、導入済みのレッスンへのリンクがあります。
5. 章タグの関連アイテムやME Guideから選んだ場面は、全教材コース内の開始位置として開きます。前後へはPonder標準の左右ボタンで移動できます。
   左下の戻る矢印で、起点のタグ画面・一覧・ME Guideへ戻れます。

章は標準タグ一覧で登録順に並びます。他のPonder対応MODのタグも標準の構成で表示します。
章の関連アイテムは登録順を保ち、画面幅と高さに合わせて配置し、収まらない分はページに分けます。
教材の題名・要約・字幕はME Guide標準の検索から探せます。

短いアイテム説明があるものは、カーソルを合わせて**Shiftキーを押す**と本文を読めます。
装飾用ブロック類とファサードはテキストで説明し、アンカーは接続を遮断する配置例もPonderで示します。
Ponderの一覧にはレッスンだけを載せ、短文だけの対象を無関係な場面へ登録しません。

設定画面を含む通常のMinecraft画面へ独自ボタンは追加しません。入口は標準のW長押しとクライアントコマンドを利用します。
レッスンを最後まで再生すると学習済みになり、`config/ae2ponder-progress.json` に保存されます。

クライアントコマンド `/ponder-ae2` からも標準タグ一覧を開けます。
章は番号・章ID・英語名・選択言語の章名、アイテムはレジストリID・IDのパス部分・表示名で指定できます。
章番号は本体の1〜6に続き、導入済みアドオンの章が7以降へ順に並びます。
アドオンの章はMOD IDまたは実名でも指定できます。旧 `addons` 指定は、最初の有効なアドオン章への別名として残しています。
既存の `/ae2ponder` も同じ操作に使えます。

```text
/ponder-ae2
/ponder-ae2 2
/ponder-ae2 network
/ponder-ae2 ae2omnicells
/ponder-ae2 Bigger AE2
/ponder-ae2 item ae2:controller
/ponder-ae2 lesson first_network
```

## 内容

素材の入手、結晶成長、プロセッサ製造、電力、最小ネットワーク、チャネル、コントローラ、
ケーブル、端末、ストレージセル、収納バス、搬入・搬出、液体、無線、セル設定、
自動クラフト、加工パターン、クラフトCPU、サブネット、P2P、量子リンク、
空間ストレージ、レベルエミッタ、プレーン、凝縮器、トラブルシューティングを扱います。

場面は動作を説明する模型です。表示用の通電状態・チャネル数・アイテム移動は演出であり、
プレイヤーのワールドを操作しません。電力源や他MODの加工機は導入環境によって異なります。
レシピ変更やチャネル設定変更があるMODパックでは、その設定を優先してください。

収納セル、投入素材、回路、アップグレードカード、パターンなどは、対象の装置に向けた
アイテム画像付きの吹き出しでも示します。複数の素材は順番に表示し、字幕と一緒に確認できます。
実際にアイテムを持って操作する場面には、右クリックやスニークの印も表示します。

## 対応言語と更新

日本語、英語、ヒンディー語、繁体字中国語（香港・台湾）、簡体字中国語（中国）、
ロシア語、ポルトガル語（ブラジル・ポルトガル）、スペイン語、フランス語、ドイツ語、
チェコ語、ポーランド語、マレー語、ルーマニア語、アラビア語、インドネシア語、
韓国語、ベトナム語、トルコ語、イタリア語に対応しています（第一・第二言語範囲）。
字幕、全レッスンのタイトルと要約、アイテムの短い説明、一覧と検索、章名、Ponder・ME Guideの操作表示、
ME GuideのページがMinecraftで選択した言語に切り替わります。

今後の追加・変更も全22言語を同時に更新します。欠落したキー、空欄、
不正なプレースホルダ、原文が変わった後に未確認の翻訳がある場合はビルドが失敗します。
日本語の説明はCreateと同じ「です・ます」調で、操作・仕組み・条件と結果を説明します。
通常操作を「〜してください」の連続にせず、必要な確認や対処の指示は残します。
ほかの言語もCreate/Ponderの自然な解説と呼びかけ方に合わせます。
見出しや短い操作ラベルは自然な形を保ちます。対応する翻訳がない場合のフォールバックは英語です。
原文の確認が済むまで翻訳ファイルの `_source_hash` を更新しないでください。
日本語・英語にも `source-review.json` で確認を記録します。
詳細は [AGENTS.md](AGENTS.md) を参照してください。

```powershell
python tools/localization.py source
python tools/localization.py check
python -m unittest discover -s tools -p test_localization.py
```

ME Guideのページは翻訳データから生成するため、ページごとの手作業の更新は不要です。

## 必要なMOD

| MOD | 1.21.1 / NeoForge | 1.20.1 / Forge |
| --- | --- | --- |
| ローダー | NeoForge 21.1.256（最低21.1.206） | Forge 47.4.27 |
| Applied Energistics 2 | 19.2.18 | 15.4.11 |
| GuideME | 21.1.19 | 20.1.15 |
| Ponder | 1.0.87+mc1.21.1 | 1.0.87 |
| Flywheel | 1.0.4 | 1.0.0-215 |
| AE2 Ponder Guide | 0.1.0-beta2 | 0.1.0-beta2 |

AE2が要求するGuideMEは必要ですが、今回のガイドはPonderから利用します。
Createが既にPonder/Flywheelを同梱している環境では、同じMODを重複導入しないでください。

## 対応アドオン（任意）

| MOD | 1.21.1 / NeoForge | 1.20.1 / Forge | ガイド内容 |
| --- | --- | --- | --- |
| AE2 OMNI Cells | 1.1.6 | 1.1.6 | 混合ストレージ、材料、携帯セル、資源型フィルター、クラフトCPU |
| Bigger AE2 | 1.21.1-1.4.9 | 20.2.1 | Quantumアイテム・液体セル、Digital Singularityセル、アクセラレーター |
| AE2 Things | 1.4.2-beta | 1.2.1 | DISKの作成、パーティション、退避と再利用 |
| AE2 MEGA Things | 2.0.4 | 1.0.8 | MEGAアイテムDISK、液体DISK、設定と再利用 |
| MEGA Cells | 4.11.0 | 2.4.6 | 大容量セル、機器、材料の短いアイテム説明 |

この5MODはガイドの必須依存ではありません。未導入の短文はツールチップから除外し、未導入のレッスンは一覧、Wキーの候補、
Ponderコース、ME Guideのページと検索から除外します。
各MODは実名ごとの独立した標準章・タグから選べ、未導入の章や空章は表示しません。
両版の実JAR・必要依存・正確な配布元は[互換性の記録](docs/addon-compatibility.md)にまとめています。
AE2 MEGA Thingsを導入する場合は、その必須依存のAE2 Things、MEGA Cells、Kotlin for Forgeも必要です。
NeoForgeの検証ではMEGA Cells 4.11.0、Kotlin for Forge 5.12.0を使用します。
ForgeではMEGA Cells 2.4.6、Kotlin for Forge 4.12.0と、MEGA Cellsが要求するCloth Config 11.1.136を使用します。

開発・テストの標準起動構成には4MODと必要な依存をすべて含めています。
アドオンなしの動作確認は `-PwithoutAddons` を付けて起動してください。
配布JARにアドオン本体は含めません。

## ビルド

1.21.1 / NeoForgeはJava 21、1.20.1 / ForgeはJava 17を使用します。
両方をビルドする場合は両JDKをインストールしてください。Gradleの実行にはJava 21も使用できます。

```powershell
.\gradlew.bat build
.\gradlew.bat -Pplatform=forge build
```

NeoForgeの出力は `build/libs/ae2-ponder-guide-0.1.0-beta2.jar`、
Forgeの出力は `build/forge/libs/ae2-ponder-guide-0.1.0-beta2-forge-1.20.1.jar` です。
Forge版は配布用にSRGへ再マッピングされ、Mixinのrefmapも含みます。
`generateGuideResources` が全場面のノード参照・座標・表示順・吹き出し・多言語テキストを検証し、
Ponder用の翻訳ファイルを生成します。構造NBTを変更する場合は `python tools/generate_structure.py` を使います。

統合検証は後述の `tools/run-validation.ps1` で共通ロックを取得して実行します。
内部では対象ローダーのGradle `runValidationClient` タスクを `-PvalidateGuide` 付きで呼び出します。
検証用コードは配布JARに含まれません。別の開発ワールドを自動作成し、ウィンドウを隠して、
Createなしで指定場面のコンパイル・再生・巻き戻し・部品復元・吹き出しのアイテムと表示順・描画、
教材を持つ4アドオンの全16通りの導入条件、通常画面に独自ボタンがないこと、標準タグ一覧・章タグ・関連アイテムへの移動、関連アイテムのページ送りと画面内配置、
W長押し、クライアントコマンド、ME Guideのリンクと復帰、Ponder標準の前後移動・戻る履歴も確認します。
`-PvalidateLanguages` を付けると22言語の読み込み・ME Guide標準検索・標準タグと操作表示を確認し、
ヒンディー語・香港繁体字・ポルトガル語・アラビア語・韓国語・ベトナム語・トルコ語の
ガイドページと字幕を描画します。
検索では選択言語の題名・要約・字幕も確認します。字幕は作者が明記した完結した文・表示単位だけで切り替え、
幅や行数による途中切断はしません。原則1吹き出し1文とし、必要な継続だけを`…\n…`で明記します。
全22言語をリソース再読み込みで切り替え、最小GUIと通常GUIで確保する180pxの字幕幅で、
各表示単位が3行以内か、文と順序が保たれるかを実フォントで検証します。
字幕描画への第二の検証用Mixinを同時に適用し、通常の呼び出しとアラビア語補正が両方動くことも確認します。
結果は `build/validation-run/validation-result.json`、画像は同ディレクトリの `screenshots` に保存します。
Forgeでは `build/forge/validation-run` に保存します。

シリーズ内のMinecraft検証は `Local\PonderSeriesValidation` ミューテックスで直列化します。
通常は次のラッパーで変更に関係する場面だけを指定してください。`-Platform forge` でForge版、
`-Languages` で言語確認、`-WithoutAddons` でアドオンなしの構成を選べます。

```powershell
./tools/run-validation.ps1 -Platform neoforge -Lessons first_network -Languages
```

字幕を変更した場合は、先に`python tools/captions.py check-all-locales`と翻訳検査を行います。
字幕の役割も`src/main/caption_styles/<locale>.json`で各言語の表示単位ごとに管理し、`python tools/caption_styles.py`で確認します。
本文色は通常が白、警告が薄い赤、ヒントが薄い緑です。文末の句点は省き、読点・版番号・小数等の点と継続の「…」を保持します。
字幕に絞った実フォント検証は`./tools/run-validation.ps1 -Platform neoforge -Captions`です。
章などのUI試験を繰り返さず、22言語の全表示単位と、代表場面の最小GUI・通常GUIでの
実描画、表示順、翻訳字幕による標準検索を確認します。

今回の通常画面・標準タグ導線・本文色・履歴に絞った検証は`./tools/run-validation.ps1 -Platform neoforge -Focused`です。
全22言語の切り替えを繰り返さず、英語で導線と色を確認した後、アラビア語の全字幕の折り返しを重点確認します。

解説文とアドオン章を同時に変更した場合は、`./tools/run-validation.ps1 -Platform neoforge -Narration`で、
同じ非表示クライアント内で重点導線試験に続けて全22言語の実フォント検証を実行します。
語句が変わった字幕は、過去の句点だけの差分検査で行数を保証せず、新しい文章を計測します。

通常ワールドの性能調査には `./gradlew -PvalidateGuide runPerformanceClient` を利用できます。
調査対象のワールドの**コピー**を `build/performance-run/saves/profile-world` に配置し、
対象の `options.txt` と必要な設定を `build/performance-run` にコピーしてから実行してください。
ガイドを開かずに45秒間カメラを回転させ、最初の15秒を除いた描画間隔・描画時間・サーバー処理時間を
`performance-result.json`、Java Flight Recorderの記録を `performance.jfr` に保存して自動終了します。
描画解像度は1280×720です。メモリ割り当ては `-PprofileMinHeap=1G -PprofileMaxHeap=4G` で変更できます。
この測定用コードも配布JARには含まれません。
Forgeでは `-Pplatform=forge` を付け、ワールド・設定・結果の保存先を `build/forge/performance-run` にしてください。
通常のForge開発起動も、既存のNeoForgeワールドを開かないよう `run/forge`（サーバーは `run/forge-server`）に分けています。

## 設計

- `src/main/java` と教材・翻訳リソース: 両バージョンで共通の実装と内容
- `src/neoforge/java` / `src/forge/java`: ローダー登録、イベント、画面API、描画データの小さな互換処理
- `src/validation/java`: 共通の検証。版別APIは `src/neoforgeValidation/java` / `src/forgeValidation/java` に配置
- `src/main/resources/assets/ae2ponder/guide/lessons.json`: 章・場面・字幕・模型・演出
- `src/main/resources/assets/ae2ponder/guide/addons.json`: 導入条件付きアドオンの場面・字幕・模型
- `GuideScenes`: 共通のPonder再生処理。AE2公式のvisual NBTでケーブルと部品を表示
- `GuidePlugin`: AE2アイテムとカテゴリへの場面登録
- `GuideNavigation`: 標準タグ一覧への入口、章・アイテム指定から既存コースの開始位置への接続
- `GuidePonderScreen`: 選んだ場面から全コースをPonder標準のナビゲーションで再生
- `GuideCommands` / `assets/ae2ponder/ae2guide`: ME Guideから各場面へのクライアント側リンク
- `PonderTagScreenMixin`: AE2の関連アイテム画面を、画面サイズに応じてページ分割
- `PonderTextWindowMixin` / `RightToLeftText`: アラビア語字幕を描画後の文字幅で折り返し、字幕枠の幅と高さを揃える
- `GuideArabicTextMixin`: ME Guideのアラビア語表示を文字幅計算前に整形し、原文の検索データを保つ
- `OptionalModTagCompiler` / `GuideDefaultExtensionsMixin`: ME Guideのアドオン節と検索を導入条件で切り替え
- `PonderLevelModelDataMixin`: NeoForgeで仮想ワールドのブロックエンティティから描画データを渡す互換処理。ForgeではPonder本体が同じ処理を行うため不要
- `src/main/templates/lang`: 日本語・英語のUI、章、Ponder操作、ガイド導入文
- `src/main/translations`: その他20言語の全テキストと原文確認のハッシュ
- `gradle/localization.gradle`: 22言語の翻訳リソースとME Guideページを生成・検証

サーバー側ではガイドクライアントのクラスをロードしません。

別MOD向けの解説を作る際の口調・教材設計・多言語対応・互換性・検証の知見は、
[MOD解説ガイドの制作ノート](docs/mod-guide-authoring.md)にまとめています。

## 一次資料

- [独立版Ponder](https://github.com/Creators-of-Create/Ponder)
- [Ponderの公式Maven](https://maven.createmod.net/net/createmod/ponder/ponder-neoforge/)
- [AE2のPonder用visual NBT](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1.21.1/ponderjs.md)
- [AE2公式ガイド](https://guide.appliedenergistics.org/1.21.1/)
- [AE2 OMNI Cells](https://github.com/Frostbite-time/AE2OmniCells)
- [Bigger AE2](https://github.com/GregTech-Exploring-Pioneer/BiggerAE2)
- [AE2 Things](https://github.com/Technici4n/AE2Things-Forge)
- [AE2 MEGA Things](https://github.com/Lapis256/AE2MEGAThings)

## 作者・ライセンス

作者・公開者は **aosankaku** です。独自のコードとガイド内容は [MIT](LICENSE) で公開しています。
Ponder由来のコード・操作文言にはThe Create Teamの著作権表示とMITライセンスを保持します。
GuideME由来の操作文言とその翻訳は **CC BY 4.0** です。GuideME本体のコードは **LGPLv3** で、
利用者が別途導入する依存MODとして使用します。

対象ファイル、原作者、出典、変更内容は [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)、
ライセンス原文は [LICENSES](LICENSES) に記載しています。これらの文書は配布JARにも同梱します。
Gradle WrapperはApache-2.0です。各依存MOD本体のライセンスはそれぞれの配布物に従います。
