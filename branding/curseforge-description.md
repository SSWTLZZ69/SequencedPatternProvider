# Sequenced Pattern Provider

**One AE2 pattern for an entire Create sequenced assembly recipe.**

Sequenced Pattern Provider connects Applied Energistics 2 autocrafting with Create's sequenced assembly system. Instead of manually creating a separate processing pattern for every step and every loop, you encode the whole sequence once and request the final product directly from your AE network.

> This project is currently in **alpha**. Back up important worlds before testing and please report reproducible problems.

## Features

- **Sequenced Assembly Pattern Encoding Terminal**
  - Encodes up to 10 processing steps per loop.
  - Supports item and fluid step materials using AE2-style virtual slots.
  - Uses normal AE2 blank patterns as the encoding material.
  - Reads Create sequenced assembly recipes through JEI's recipe transfer button.
- **Master Pattern Provider**
  - Stores multiple sequenced assembly patterns.
  - Exposes their final products to AE2 autocrafting.
  - Tracks several active assembly jobs at once.
  - Has a configurable concurrency limit and an emergency abort function.
- **Child Pattern Provider**
  - Routes each processing step to the correct machine or input inventory.
  - Can handle one or more assigned processing types.
  - Includes an AE2-style blocking mode to avoid mixing batches.
  - Works with ordinary item/fluid targets and ME Interface-style return paths.
- **Reliable intermediate-item tracking**
  - Intermediate workpieces are matched with their complete AE key data instead of only checking the base item.
  - Progress is event-driven when items return to the AE network, avoiding continuous full-network scans.
- **In-game documentation**
  - The complete guide is integrated into AE2's GuideME guide.
  - Jade status information and JEI recipe transfer are supported when installed.

## Basic Setup

1. Connect a master provider and one or more child providers to the same powered AE2 network.
2. Point each child provider toward the machine or input inventory it should supply.
3. Assign processing types to the child providers and link them to the master.
4. Open the sequence encoding terminal and use the **+** button on a Create sequenced assembly recipe in JEI.
5. Insert an AE2 blank pattern and encode the sequence.
6. Put the encoded pattern into the master provider.
7. Request the final product from an AE2 terminal as a normal crafting job.

## Requirements

- Minecraft 1.20.1
- Forge 47+
- Applied Energistics 2 15.4.10+
- Create 6.0.8+
- GuideME 20.1.x

Optional integrations:

- JEI 15.20+
- Jade 11.13+

## Pattern Recovery

Shift-right-click an encoded sequenced assembly pattern to convert it back into a normal AE2 blank pattern. The clear button in the encoding terminal also clears the editor and safely returns the encoded pattern as a blank pattern.

---

# 中文说明

**用一个 AE2 样板完成整条机械动力序列组装。**

序列组装样板供应器把 AE2 自动合成与机械动力的序列组装连接起来。玩家不再需要为每一步、每次循环分别制作处理样板，只需编码一次完整流程，就能直接在 AE 网络中下单最终产物。

> 当前版本处于 **Alpha 测试阶段**。建议在重要存档中使用前做好备份，并反馈可以稳定复现的问题。

## 主要功能

- **序列组装样板编码终端**
  - 每轮最多编码 10 个加工步骤。
  - 使用 AE2 风格虚拟槽位记录物品和流体材料。
  - 直接消耗 AE2 原版空白样板。
  - 支持通过 JEI 配方页面的 **+** 自动读取机械动力序列组装配方。
- **主控供应器**
  - 可以存放多个序列组装样板。
  - 让 AE2 自动合成系统识别对应的最终产物。
  - 可以同时管理多个序列组装任务。
  - 支持调整并发上限，并提供紧急中止功能。
- **子供应器**
  - 把每一步需要的工件和材料送往对应机器或输入容器。
  - 一个子供应器可以负责一种或多种加工类型。
  - 提供类似 AE2 样板供应器的阻挡模式，避免不同批次混在一起。
  - 支持普通物品、流体目标以及通过 ME 接口返回网络的产线。
- **可靠的中间工件识别**
  - 使用完整 AE Key 数据识别工件，而不只是比较基础物品 ID。
  - 工件返回 AE 网络时通过事件推进任务，不需要持续扫描整个网络。
- **游戏内说明**
  - 完整教程直接加入 AE2 自带的 GuideME 指南。
  - 安装 Jade 和 JEI 后可获得状态显示与配方自动填充功能。

## 快速使用

1. 把主控供应器和一个或多个子供应器接入同一个有电的 AE2 网络。
2. 让每个子供应器的正面朝向它负责的机器或输入容器。
3. 为子供应器设置加工类型，并将它们连接到主控。
4. 打开序列组装编码终端，在 JEI 的机械动力序列组装配方页面点击 **+**。
5. 放入一个 AE2 空白样板并进行编码。
6. 把编码完成的样板放入主控供应器。
7. 在 AE2 终端中像普通自动合成一样下单最终产物。

## 样板还原

手持已编码序列组装样板潜行右击，可以把它还原成 AE2 空白样板。编码终端中的清除按钮也会清空当前设置，并安全退回空白样板。

## 协议

MIT License
