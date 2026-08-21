# 顛末書: 性格対応におけるアーキテクチャ変更提案の不備

- Incident ID: NPCB-INC-20260821-01
- Date: 2026-08-21
- Project: NPCBrain
- Affected version: v0.1.3 (design proposal only)
- Severity: Design-process defect / pre-implementation
- Code impact: **None. The personality proposal was not implemented in NPCBrain source code.**
- Status: Corrective action in progress; architecture topology frozen pending explicit approval.

## 1. 概要

NPCBrain の実機動作で「AIアシスタントのような回答になる」という問題が確認された後、人格心理学・人格神経科学を追加調査した。その際、既存の 9 専門領域 + Global Workspace に対して `affective_appraisal`、`social_cognition`、`character_renderer` などの新規モジュール追加を推奨した。

この提案は、人格・感情・社会認知に関する研究から「重要な心理過程である」ことを確認した一方で、**それらを NPCBrain の独立モジュール境界にする必要性を立証していなかった**。心理学上の概念をそのままソフトウェアモジュールへ写像し、既存設計への影響分析・代替案比較・アブレーション基準・変更承認を経ずにアーキテクチャ変更として提示した点が不備である。

## 2. 影響

### 実装影響

人格対応の新規モジュール提案は NPCBrain 本体には実装されていない。v0.1.3 の `BrainEngine` は以下の既存構成のままである。

1. perception
2. salience
3. episodic_memory
4. semantic_memory
5. world_model
6. executive_control
7. valuation
8. error_monitor
9. action_selection
10. Global Workspace

したがって、今回の不備によるコードの出戻りはまだ発生していない。

### 設計・知識影響

共有ナレッジに「新規モジュール追加」を推奨するプロジェクト設計案が保存され、将来の実装判断を誤らせる状態になった。これは是正対象である。Git履歴は削除せず、当該提案を superseded として残し、再監査結果へ参照を切り替える。

## 3. 時系列

1. 初回調査では、脳を機能単位に分解するため、知覚、注意、作業/長期記憶、世界モデル、実行制御、価値判断、誤り監視、行動選択、Global Workspace 等を調査した。
2. その調査は「人間一般に共通する認知機能の機能分解」を主対象としており、人格・気質・自己同一性などの安定した個人差は明示的な対象に含めていなかった。
3. NPCBrain v0.1.x はその一般認知機能のサブセットを 9 専門領域 + Global Workspace として実装した。
4. 実機で、最終出力が NPC ではなく AI アシスタント的になる問題が確認された。
5. 人格研究を追加調査し、性格が注意・記憶・価値判断・行動などを横断的に変える点を確認した。
6. しかし、その研究結果から独立モジュール追加へ短絡し、既存トポロジーを維持した代替案の検討を十分に行わなかった。
7. ユーザー指摘により設計変更提案を停止。再監査を実施した。

## 4. 根本原因

### RC-1: 要求スコープの混同

初回の「脳機能分類」は species-general な認知機能を対象にしていた。一方、NPC として自然に振る舞うためには、個人差としての人格、目標、価値観、関係性、自己モデルまで必要である。この二つを同じ「人の脳」という言葉で扱い、初回設計の適用範囲を明示しなかった。

### RC-2: 心理概念をソフトウェア境界へ直接写像した

人格神経科学・感情神経科学・社会神経科学は、単一機能 = 単一領域という単純な対応を支持していない。人格は分散した神経系の安定した個人差として研究されており、感情・社会認知も複数ネットワークの相互作用で実現される。したがって、「研究対象として独立概念である」ことは「独立 LLM モジュールにすべき」根拠にはならない。

### RC-3: モジュール追加の工学的立証不足

NPCBrain が採用した脳着想型モジュール設計の主要根拠である Webb et al. (Nature Communications, 2025) では、各専門モジュールをアブレーションし、性能への寄与を検証している。今回の追加提案には以下が欠けていた。

- 既存モジュールでは表現できない独立した失敗モード
- 明確な入力/出力/状態所有境界
- 追加前後の比較実験
- アブレーションで予測される選択的欠損
- コスト・遅延・複雑性増加に見合う効果

### RC-4: 変更管理ゲートがなかった

アーキテクチャ変更を「研究結果の説明」と同じ流れで提案し、変更理由、影響範囲、代替案、移行、ロールバック、テスト、承認を必須化していなかった。

## 5. 再監査で確認した証拠

### 人格は既存制御機構のパラメータとして扱える

DeYoung (2015), Cybernetic Big Five Theory, Journal of Research in Personality, DOI 10.1016/j.jrp.2014.07.004 は、人格特性を evolved cybernetic mechanisms のパラメータ差として扱い、characteristic adaptations を更新可能な記憶上の goals / interpretations / strategies と位置づける。

Denissen & Penke (2008), Journal of Research in Personality, DOI 10.1016/j.jrp.2008.04.002 は、Big Five を特定の環境刺激に対する安定した motivational reaction differences として説明する。これは人格を新しい処理段階ではなく、既存の注意・動機づけ・目標追求・行動選択の反応特性として実装する根拠になる。

### 人格の単一脳領域対応は支持が弱い

Hilger & Markett (2021), Network Neuroscience, DOI 10.1162/netn_a_00198 は personality network neuroscience を提唱し、人格特性が特定の分離可能な biophysical entity に対応するか自体が未解決であると整理する。

Chen & Canli (2022), Personality Neuroscience, DOI 10.1017/pen.2021.5 の Big Five と脳構造の系統レビュー/メタ解析では、頑健に再現できる構造差を確認できなかった。

### 感情・社会認知も分散ネットワーク

Lindquist & Barrett (2013), Current Opinion in Neurobiology, DOI 10.1016/j.conb.2012.12.012 は、感情・社会認知・非社会的認知を domain-general な大規模分散ネットワークの相互作用として説明する。

Schurz et al. (2020), Cortex, DOI 10.1016/j.cortex.2020.05.006 は Theory of Mind、共感、行為観察が複数ネットワークの統合/分離を利用することをレビューしている。

したがって、`affective_appraisal` や `social_cognition` を独立 LLM ノードとして新設することは、神経科学から直接は導けない。

## 6. 是正判断

### 撤回

以下の「独立モジュールとして追加する」提案は撤回する。

- `affective_appraisal` 新設
- `social_cognition` 新設
- `character_renderer` 新設

重要な機能であること自体は否定しない。ただし現時点では既存 9+Global Workspace の内部状態・プロンプト・データ契約で表現できる可能性が高く、モジュール境界変更を正当化する証拠が不足している。

### 凍結

v0.1.3 の 9 専門領域 + Global Workspace のトポロジーを凍結する。明示的な承認を得るまで、専門領域の追加・削除・統合・順序変更は行わない。

## 7. 再設計方針

人格は新しい「人格脳」を作るのではなく、既存認知サイクルへ横断入力する。

- perception: 観測事実は人格から極力分離する。
- salience: 報酬、脅威、未知、義務、対人情報への重みを人格で変える。
- episodic_memory: 記憶内容は保存し、検索順位に人格/現在状態のバイアスを限定的に入れる。
- semantic_memory: 世界知識に加え、価値観、目標、関係性、自己像、習慣を characteristic adaptations として保持する。新規メモリモジュールには分けない。
- world_model: 客観的な起こりやすさと、本人が重視する懸念/期待を分離して出す。
- executive_control: 目標維持、探索、方針転換、粘りのパラメータへ人格を反映する。
- valuation: 人格反映の中心。報酬、危険、協力、探索、義務等の価値重みを変え、動的な感情/動機状態もここで扱う。
- error_monitor: 客観的エラー検出と、ミスをどれだけ気にするかを分離する。
- action_selection: 客観的候補から人格重み付き価値に基づいて行動を選ぶ。
- Global Workspace: 各機能と人格状態を統合する。`helpful answer` を最適化するのではなく、NPC の選択行動と発話を統合する。これは既存モジュールの責務修正であり、ノード追加ではない。

## 8. 「AIっぽい回答」の直接原因

現在の `BrainEngine.globalWorkspacePrompt` は最終統合器へ `produce one coherent response and one concrete next action` を要求し、出力契約にも `final answer`、`action`、`rationale` がある。さらに UI 表示も `次の行動:` と `理由:` を付加する。この契約自体がアシスタント的な回答形式を誘発している。

これは人格不足とは別の直接原因であり、トポロジー変更をせず Global Workspace の出力契約を NPC 行動・発話へ変更することで対処可能である。ただしこの契約変更も設計変更なので、別途承認後に実装する。

## 9. 再発防止

アーキテクチャ変更は `docs/architecture/change-control.md` のゲートを必須とする。特にモジュール境界変更は、研究上の名称一致ではなく、独立失敗モード・インターフェース・アブレーション・費用対効果で立証する。

## 10. 現時点の安全な状態

- NPCBrain ソースコード: 人格対応の新規モジュール変更なし。
- 現行バージョン: v0.1.3 / versionCode 4。
- 現行トポロジー: 9 specialist + Global Workspace。
- 本顛末書以降、明示承認までトポロジー変更禁止。
