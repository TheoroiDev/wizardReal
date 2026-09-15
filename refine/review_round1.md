# G2P 精炼 Round 1

## 对象
- wizardreal feat/g2p：`com.theo.wizardreal.g2p`（G2p/PinyinIpa/KanaIpa/HangulIpa）、ServerVoiceCast.withDrafts、[voice] g2pDrafts 配置、测试与 pinyin 数据表（26,703 字）
- ipa lab：`ipa/g2p/` 拷贝（Tier-0 改 vocab.tsv）、bench g2p-serve、gen_ipa.py（769 条草案已落盘 75 法术 JSON）

## 目标
为未知/自定义咒语词生成可用的 espeak 风格 IPA 草案；草案经回测闸门验证前不得进识别器；精选模板永不被覆盖。

## Round 1 维度与指标
1. **转换正确性**（高价值优先）：对给定输入清单（zh 声韵组合/ü/儿化/多音字、ja 促音长音拗音浊音、ko 元音收缩、混合文本）逐一核对产出；错误 = 实际输出与标准 espeak 风格 IPA 不符或异常。指标：零 P0（崩溃/死循环）、P1 语言映射错误清单化。
2. **覆盖完整度**：75 法术的草案产出率（条目级：有草案条目/总空字段条目）；zh 行覆盖率应 >95%，ja 汉字行与 en 的空缺须有文档化归因。
3. **工程/管线**：gen_ipa.py 幂等性（重跑零 diff）、g2p-serve 批量速率、lab 拷贝与生产的漂移控制、withDrafts 语言裁剪边界、测试覆盖缺口。
4. **可维护性**：javadoc 与行为一致、文档（docs/g2p/）与实现一致性、资源加载失败行为。

## 评审发现

### A · 语言转换正确性（subagent 语言审计）
- **P0×2（KanaIpa）**：っ+元音开头假名产出重复元音垃圾（っあ→"a a"）；ー 词首被静默丢弃
- **P1 zh×4**：哟/唷（io 韵母无键→整段拒绝）；嗯/呣/噷（零韵母音节被声母吞掉→整段拒绝）；
  独立 o→wɔ（仅 bo/po/mo/fo 该如此）；l/n+ü 用 palatal ɥ（略→lɥɛ 应 lyɛ）
- **P1 ja×3**：ゔ/ヴ 全家族无法转换（游戏命名高频）；ヷヸヹヺ越过 shift 窗口；ンー 盲加 ː
- **P2×8**：增补平面汉字误判 LATIN、NBSP 不分段、数字/emoji 全拒未文档化、lang=en 汉字泄漏
  拼音、OTHER 段吸收异脚本、"+"ui 死分支、substring 码元迭代、零韵母外死代码
- **ko**：ㅢ→i 丢滑音（改 ɰi）；空格死分支
- 数据表卫生：无声调数字/大写/ASCII 键 ✓；覆盖 Ext A+基本区与 scriptOf 一致 ✓
- 完整报告含 14 条测试补充清单

### B · 工程管线（subagent 工程审计）
- **P0×1**：**草案自反馈僵死**——vocab.tsv 已含机器草案，gen_ipa 的 Tier-0 又读它 →
  normalize/Tier-1 改进永远传不到已落盘条目
- **P1×6**：G2p.invalidate() 全仓零调用（/reload 后 Tier-0 持旧快照）；gen_ipa 无 --refresh；
  normalize 只对 CJK 剥标点（en 咏唱行 Tier-2 落地后仍全灭）；trigger.ipa 跨语言草案无全局
  去重（fulmen 三条相同 ˈfʊlmɛn）；withDrafts 只取第一启用桶且不 fallback（与 gen_ipa 双轨
  不一致）；normalize 0x2E80 分支死逻辑
- **P2×5 / P3×6**：漂移无校验机制；g2p-serve 协议（lang 白名单/bye 误杀）；stderr DEVNULL、
  无重启续跑；测试缺口（幂等性/多桶/golden）；769 条草案无 provenance；无参 pushVocabulary
  死代码等
- **备案**：zh 草案逐字风格与 curated 一致无需返工；g2pDrafts 默认 false 文档化合规

## 修复动作（全部落地）
1. **P0 自反馈**：gen_ipa Tier-0 改用 curated-only `vocab_legacy.tsv`，经新增
   `bench g2p-serve --tier0` 参数传入（ipa 5039ec0）
2. **KanaIpa 重写**：ゔ/ヴ→bɯ、ー 词首/非元音尾 strict、っ+元音丢促音标记不产垃圾、
   独立小拗音不再吞前音、ヷヸヹヺ排除文档化（wizardreal d01c9b5）
3. **PinyinIpa**：io→jo、零韵母→音节性鼻音、独立 o→o、l/n+ü→y 主元音、删 ui 死分支/no-op
4. **G2p**：scriptOf 增补平面 HAN、NBSP/SpaceChar 分段、lang=en Han 拒绝、OTHER 段不吸收
   异脚本；HangulIpa ㅢ→ɰi、删死分支
5. **withDrafts 全启用桶聚合+全局去重**（对齐 gen_ipa）；G2p.invalidate() 接线 /reload；
   删无参 pushVocabulary 死代码
6. **同步与守卫**：lab 四类重同步（生产为真源）；新增 `ipa/check_g2p_sync.py` 漂移守卫
   （Tier-1 三类逐行一致，G2p.java 豁免——Tier-0 两仓异构为设计）
7. **测试**：+14 条审计回归用例（哟/嗯/哦/略/花儿/っあ/ービール/ヴァル/ヷ/의사/数字/NBSP/
   增补平面/Han-en）+ ServerVoiceCastDraftTest 多桶；全测试绿（114）
8. **草案重生成**：修复后 G2P 全量 --refresh（770 条，+1 条此前失败条目转成功；40.2%）

## 遗留项（含阻塞原因）
- en Tier-2（Sphinx-4 WFST）：docs/g2p/02 分期 P1，非本轮 scope
- ja 汉字行：需形态学分析器（docs C4），CHANGELOG 已声明
- provenance sidecar（草案/精选不可区分）：P2，sidecar 格式待定，下轮或独立任务
- gen_ipa 断线重启续跑：P2，批量亚秒级完成，实际收益低
- 幂等性 pytest：P2，gen_ipa 为 lab 脚本无测试框架，待 lab 测试基建
- 多音字（行/重/长）：默认音已文档化，词级覆盖靠 Tier-0 策展兜底（既有设计）

## 修复动作
（待回填）

## 遗留项
（待回填）

---

# Round 2（窄域复审 + 幂等性/质量自测）

## 窄域复审（未参与修复的 subagent，9 项清单）
- **8.5/9 通过**；唯一 FAIL：smallGlide 分支的 head.isEmpty() 保留逻辑在重写时遗漏
  （あゃ 实测仍 "ja"，与提交声明/注释矛盾）——已复核暴露
- 复审新发现：smallVowel 注释例证错位（あゃ vs あぁ）；PinyinIpa 零韵母注释举噷 hm 不实 +
  s.equals("h") 防御性死分支；ServerVoiceCast 桶 key 大小写潜伏项；WizardReal SERVER_STARTED
  与 registerAll 时序（信息级，终态正确）

## 复审修复（全部落地，wizardreal 2a1a9d1）
- smallGlide 独立元音分支：保留前音节 token（あゃ -> "a ja"）+ 回归测试
- PinyinIpa 零韵母注释改为真实可及用例（嗯/呣），删 h 兜底死分支
- ServerVoiceCast 桶 key lowercase 后再传 G2p

## Round 2 自测（质量与管线指标）
- **幂等性**：gen_ipa --refresh 连跑两次，第二次 0 文件变更 ✓
- **zh 草案质量人工抽检 4/4 全对**（逐字拼音核对）：
  以雷暴之名应召 -> i leɪ paʊ ʈʂɨ mɪŋ ɪŋ ʈʂaʊ ✓
  织电光入我言 -> ʈʂɨ tjɛn kwɑŋ ʐu wɔ jɛn ✓
  唤醒赤红雷霆 -> xwan ɕɪŋ ʈʂʰɨ xʊŋ leɪ tʰɪŋ ✓
  大地为之崩裂 -> ta ti weɪ ʈʂɨ pəŋ ljɛ ✓
- 草案产出率 40.2%（770/1917；zh 100%，ja 限假名行，en 待 Tier-2——归因不变）
- lab g2p-serve 冒烟：あゃ→"a ja"、哟哟→"jo jo"、熔甲→"ʐʊŋ tɕja"、嗯→"n" 全过

## 判定
R1 评审的 P0/P1 全部修复并经窄域复审确认；复审 FAIL 项已二次修复+测试钉住；R2 自测无新
P0/P1。剩余遗留均为跨轮独立任务（en Tier-2、ja 形态分析器、provenance sidecar、lab pytest
基建、多音字 Tier-0 策展）。**收口。**
