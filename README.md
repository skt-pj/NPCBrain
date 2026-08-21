# NPCBrain

Androidで、脳の認知機能を複数の生成AI役割へ分解して協調させる実験アプリです。

## Cognitive architecture

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

各モジュールは OpenAI Responses API の `gpt-5.6-luna` を `reasoning.effort=max` で呼び出し、構造化JSONを返します。Global Workspaceが各結果を統合します。

## Brain monitor

思考実行中は、各専門モジュールについて次を画面の「脳内モニター」へ逐次表示します。

- 状態（待機 / 思考中 / 完了 / 停止）
- 公開用の短い判断要約
- 信頼度
- 最大3件の注目事実

逐語的な内部推論やchain-of-thoughtは表示・保存しません。

## Memory

- working memory: 現在の認知サイクルだけで利用
- episodic memory: 最大32件の経験
- semantic memory: 最大24件の長期知識
- retrieval: 関連度・重要度・新しさを使って必要な記憶だけ取得

## Network behavior

`INTERNET` と `ACCESS_NETWORK_STATE` を使用します。OpenAI接続時はAndroidが認識しているインターネット利用可能なネットワークを順に試します。DNSや接続がすべて失敗した場合は、Private DNS、VPN、広告ブロッカー、Wi-Fiなどを確認できる日本語エラーを表示します。

OpenAI APIキーはアプリ右上メニューから入力し、端末の Android Keystore で暗号化して保存します。APIキーはリポジトリやAPKへ埋め込みません。

Version: 0.1.3 / versionCode 4
