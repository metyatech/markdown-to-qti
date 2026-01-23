<!-- markdownlint-disable MD025 -->
# Languages and writing

## 言語（返答・記述）

### 回答言語

ユーザーへの最終返答は日本語で書く（ユーザーから別の希望がある場合はそちらを優先）。

### 記述言語

- 特に指定がない限り、開発者向けドキュメント（例: `README.md`）、コードコメント、コミットメッセージは英語で書く。

## 実装・技術選定

- JavaScript ではなく TypeScript を標準とする（`.ts`/`.tsx`）。
- JavaScript は、ツール都合で必要な設定ファイル等に限定する。
- 既存の言語/フレームワーク/依存関係の範囲で完結させる。新規依存追加は必要最小限にする。
- 対象ツール/フレームワークに公式チュートリアルや推奨される標準手法がある場合は、それを第一優先で採用する（明確な理由がある場合を除く）。
- 「既存に合わせる」よりも「理想的な状態（読みやすさ・保守性・一貫性・安全性）」を優先する。
- ただし、目的と釣り合わない大改修や無関係な改善はしない。
- 不明点や判断が分かれる点は、独断で進めず確認する。
- 推測だけで判断して進めない。根拠が不足している場合は確認する。

### 意思決定の優先順位

保守性 ＞ テスト容易性 ＞ 拡張性 ＞ 可読性

## 設計・実装の原則（共通）

- 責務を小さく保ち、関心を分離する（単一責任）。
- 依存関係の方向を意識し、差し替えが必要な箇所は境界を分離する（抽象化/インターフェース等）。
- 継承より合成を優先し、差分を局所化する（過度な階層化を避ける）。
- グローバルな共有可変状態を増やさない（所有者と寿命が明確な場所へ閉じ込める）。
- 深いネストを避け、ガード節/関数分割で見通しを保つ。
- 意図が分かる命名にする（曖昧な省略や「Utils」的な雑多化を避ける）。
- ハードコードを避け、設定/定数/データへ寄せられるものは寄せる（変更点を1箇所に集約する）。
- 変更により不要になったコード/ヘルパー/分岐/コメント/暫定対応は、指示がなくても削除する（残すか迷う場合は確認する）。

## コーディング規約

- まずは各リポジトリの既存コード・設定（formatter/linter）に合わせる。
- 明示的な規約がない場合は、対象言語/フレームワークの一般的なベストプラクティスに合わせる。

## コマンド実行

- ユーザーが明示しない限り、コマンドにラッパーやパイプを付加しない。
- ビルド/テスト/実行は、各リポジトリの標準スクリプト/手順（`package.json`、README等）を優先する。

# 品質（テスト・検証・エラーハンドリング）

## 検証（ビルド/テスト/静的解析）

- 変更に関連する最小範囲のビルド/テスト/静的解析を実行する。
- 実行方法は各リポジトリが用意しているスクリプト/コマンドを優先する（例: `npm run build`, `npm test`）。
- 実行できない場合は、その理由と、ユーザーが実行するコマンドを明記する。

## テスト

- 原則: 挙動が変わる変更（仕様追加/変更/バグ修正/リファクタ等）には、同一変更セット内で自動テスト（ユニット/統合/スナップショット等）を追加/更新する（必須）。
- 網羅性: 変更箇所の分岐・状態遷移・入力パターンについて、結果が変わり得るすべてのパターンを自動テストで網羅する（必須）。少なくとも「成功/失敗」「境界値」「無効入力」「代表的な状態遷移（例: 直前状態の影響、切り替え、解除/復帰）」を含める。
- 失敗系: 期待されるエラー/例外/不正入力の失敗ケースも必ずテストする（必須）。
- テスト未整備: 対象リポジトリにテストが存在しない場合は、可能な限り最小のテスト基盤とテストを同一変更セット内で追加する。新規依存追加が必要な場合は、候補と影響範囲を提示してユーザーに確認してから進める。
- 例外: テスト追加や網羅が困難/不適切な場合は、理由と不足しているパターン（カバレッジギャップ）を明記し、代替検証（手動確認手順・実行コマンド等）を提示してユーザーの明示許可を得る（独断で省略しない）。
- テストは決定的にする（時刻/乱数/外部I/O/グローバル状態への依存を最小化し、必要なら差し替え可能にする）。
- Playwright のテストが動作しない場合は、`playwright/.cache` を削除してから再実行する（例: `npm run test-ct:clean`）。

## バグ修正（手順）

バグ修正は原則として、次の順で行う:

1. バグを再現する自動テストを追加/更新し、テストが失敗することを確認する。
2. バグ修正を行う。
3. 関連するテストを実行し、修正によってテストが通ることを確認する。

上記の自動テスト追加が困難な場合は、理由と代替検証手順を明記し、ユーザーに確認してから省略する。

## エラーハンドリング

- 失敗を握りつぶさない（空の catch / 黙殺 / サイレントフォールバックを避ける）。
- 回復可能なら早期 return + 明示的なエラー通知、回復不能なら明確に停止/失敗させる。

## ログ

- ログは冗長にしないが、原因特定に必要なコンテキスト（識別子や入力条件）を含める。
- 秘密情報/個人情報をログに出さない（必要ならマスク/分離する）。

# 生成物

- 生成物（例: `build/`, `dist/`, `node_modules/`）は原則コミットしない（各リポジトリの `.gitignore` に従う）。

## Module system (ESM)

- Always set `"type": "module"` in `package.json`.
- Prefer ESM with `.js` extensions for JavaScript config and scripts (e.g. `next.config.js` as ESM).

# AGENTS ルール運用（合成）

## 対象範囲

- この `AGENTS.md` は単独で完結する前提とする。
- 親子ディレクトリの `AGENTS.md` に依存しない（継承/優先の概念は使わない）。
- ルールは共通ルール（`agent-rules/rules/`）として管理し、各プロジェクト直下の `agent-rules/`（git submodule）から参照して合成する。
- プロジェクト固有ルールが必要な場合は、プロジェクト側に `agent-rules-local/` 等で配置し、`agent-ruleset.json` から参照して合成する。

## 更新方針

- ルール変更は `agent-rules/rules/`、プロジェクト固有ルール（例: `agent-rules-local/`）、および `agent-ruleset.json` に対して行い、合成スクリプト（`agent-rules-tools/tools/compose-agents.cjs`）で `AGENTS.md` を再生成する。
- 生成済みの `AGENTS.md` は直接編集しない（編集が必要なら元ルールへ反映する）。
- ユーザーから「ルールを更新して」と依頼された場合、特段の指示がない限り「適切なルールモジュールとルールセットを更新し、再生成する」ことを意味する。
- ユーザーが「常にこうして下さい」など恒常運用の指示を明示した場合は、その指示自体をルールとして適切なモジュールに追記する。

## ルール修正時の注意点

- MECE（相互排他的かつ全体網羅的）に分類し、重複と漏れを作らない。
- 冗長な説明や同じ内容の繰り返しを避ける（必要十分）。
- 手順や指示は、何をすれば良いかが一読で分かる端的な表現で書く。
- 手順以外の列挙に番号を振らない（追加/削除で保守が崩れるため）。
- 各セクションの役割を明確にし、「どこに書くべきか」が一目で分かる構成にする。

## AGENTS.md の配置

- 各プロジェクトのルートに `AGENTS.md` を置く。
- サブツリーに別プロジェクトがある場合のみ、そのルートに `AGENTS.md` を置く（同一プロジェクト内で重複配置しない）。

## ドキュメント（README）

- すべてのリポジトリ（モジュール）に `README.md` を置く。
- README には最低限として、概要/目的、セットアップ、開発コマンド（例: build/test/lint）、必要な環境変数/設定、公開/デプロイ手順（該当する場合）を書く。
- ソースコード変更時は、README へ影響がないかを必ず確認する。影響がある場合は同一変更セット内で README を更新する（必須）。
  - 影響例: 使い方/API/挙動、セットアップ手順、開発コマンド、環境変数、設定、公開/デプロイ手順、対応バージョン、破壊的変更。
  - README 更新が不要な場合でも、「なぜ不要か」を最終返答に明記する（独断でスキップしない）。

# Project Rules: markdown-to-qti (Kotlin)

## Scope

- This repository will implement a tool that converts Markdown content into IMS QTI 3.0.
- Prioritize correctness of QTI 3.0 output and a clean internal data model.

## Kotlin / Gradle conventions

- Prefer Kotlin (JVM) with Gradle.
- Keep the entrypoint small; put logic into testable functions/classes.
- Favor immutable data, explicit types at boundaries, and clear error types.

## QTI 3.0 output rules

- Output must be valid QTI 3.0 XML (well-formed, schema-aligned).
- Keep identifiers stable and deterministic (avoid random IDs unless explicitly required).
- When behavior is ambiguous, prefer standards-compliant conservative output and document the decision.

## Markdown conversion behavior

- Define supported Markdown features explicitly (e.g., headings, lists, code blocks, inline formatting).
- Unsupported constructs should fail fast with actionable messages, or be safely downgraded with clear warnings.
- Preserve exact formatting as much as possible when mapping to QTI.

## Testing expectations

- Add unit tests for parsing/mapping rules.
- Add golden tests for QTI XML output (compare normalized XML).
- Include a few end-to-end fixtures (Markdown input -> QTI output) under a dedicated test folder.

## CLI / UX

- Provide a simple CLI with:
  - input path(s)
  - output directory
  - validation mode (validate-only)
  - verbose logging
- Error messages must include the source location when possible (file + line/column).
