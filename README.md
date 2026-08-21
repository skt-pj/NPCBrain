# NPCBrain

Androidで、人間の認知機能を複数の GPT-5.6 Luna 呼び出しへ分解し、同じ人物の性格・記憶・価値観を横断的に反映させる実験アプリです。

## Cognitive architecture

トポロジーは固定です。

1. perception / 知覚
2. salience / 注意・重要度
3. episodic_memory / エピソード記憶
4. semantic_memory / 意味記憶
5. world_model / 世界モデル・予測
6. executive_control / 実行制御・計画
7. valuation / 価値判断
8. error_monitor / 誤り監視
9. action_selection / 行動選択
10. global_workspace / 統合

各段階は OpenAI Responses API の `gpt-5.6-luna` を `reasoning.effort=max` で呼び出します。人格対応で新しい「性格脳」は追加していません。

## Personality

右上メニューの `人格設定` から次を設定できます。

- 名前
- Big Five（外向性、神経症傾向、協調性、誠実性、開放性）
- 役割・自己像
- 価値観
- 目標
- 恐れ
- 人間関係
- 話し方

Big Five と現在の感情状態は共有 `CharacterState` として既存モジュールへ渡します。役割・価値観・目標・恐れ・関係性は既存の意味記憶へ型付きで保持します。

人格は事実を改変する設定ではありません。主に注意、記憶検索、予測上の懸念、実行制御、価値判断、誤りへの反応、行動選択へ確率的なバイアスとして反映します。

## Output

v0.2.0 では Global Workspace の最終契約を NPC 向けに変更しました。

- 製品出力: NPC が実際に言う発話と、実際に取る行動
- 脳内モニター: 各既存領域の公開用判断要約、人格影響、信頼度、注目事実

`final answer + 次の行動 + 理由` というアシスタント形式は製品出力から外しました。逐語的な内部推論や chain-of-thought は表示・保存しません。

## Memory

- working memory: 現在の認知サイクルのみ
- episodic memory: 最大32件
- semantic memory: 最大24件
- typed character adaptations: 既存 semantic memory 内
- retrieval: 直接関連度を主とし、人格関連度は小さい補助項として使用

v0.1.x の意味記憶は後方互換で読み込みます。`type` がない既存項目は `world_fact` として扱います。

`記憶を消去` は学習した経験・意味記憶を消しますが、人格設定で指定した価値観・目標・関係性などは保持します。

## Network and API key

`INTERNET` と `ACCESS_NETWORK_STATE` を使用します。OpenAI接続時はAndroidが認識している利用可能なインターネット回線を順に試し、DNS/接続失敗時は日本語の診断を表示します。

OpenAI APIキーはアプリ右上メニューから入力し、Android Keystoreで暗号化して保存します。APIキーはリポジトリ、APK、長期記憶へ埋め込みません。

## Architecture change control

人格対応の再監査と変更管理は以下に記録しています。

- `docs/incident-reports/2026-08-21-personality-architecture.md`
- `docs/architecture/personality-integration-audit.md`
- `docs/architecture/change-control.md`
- `docs/architecture/changes/2026-08-21-personality-v020.md`

Version: 0.2.0 / versionCode 5
