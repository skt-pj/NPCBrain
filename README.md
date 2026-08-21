# NPCBrain

Androidで、脳の認知機能を複数の生成AI役割へ分解して協調させる実験アプリです。

## MVP architecture

1. perception
2. salience
3. episodic_memory
4. world_model
5. executive_control
6. valuation
7. error_monitor
8. action_selection
9. global_workspace

各モジュールは OpenAI Responses API の `gpt-5.6-luna` を `reasoning.effort=max` で呼び出し、JSONだけを返します。Global Workspaceが各結果を統合します。

OpenAI APIキーはアプリ右上メニューから入力し、端末の Android Keystore で暗号化して保存します。APIキーはリポジトリやAPKへ埋め込みません。

Version: 0.1.0 / versionCode 1
