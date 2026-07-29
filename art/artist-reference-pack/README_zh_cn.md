# 序列组装样板供应器美工参考包

本包用于 Minecraft 1.20.1，所有目标纹理均为 16×16 PNG。它可以直接作为资源包加载，也可以解压后用 Blockbench 查看模型。

## 设计说明

**主控供应器：** 六个面完全相同。主体为黄铜控制方块，每个面中央包含深色AE控制面板、样板芯片和“两个青色步骤节点、一个金色结果节点、黄铜折返线路”组成的序列标记。

**子供应器：** 主体为安山机壳与米白色AE接口板。不指向状态六面相同，中央是四向分配接口；指向状态的输出面是凹入方形接口，背面是封闭盖板，其余四面使用朝向出口的黄铜箭头。

**已编码序列组装样板：** 保持AE处理样板的倾斜芯片轮廓，中央使用两个青色步骤节点、一个金色结果节点和一条黄铜折返线路。

**供应器连接器：** 一件双端连接探针，一端为安山方形插头，一端为黄铜环形探头，中间由深色握柄和青色线路连接。

**序列组装样板编码终端：** 保持AE编码终端的扁平部件轮廓，只在中央屏幕显示序列节点和循环线路。

## 需要替换的纹理

- `textures/block/master_pattern_provider.png`
- `textures/block/child_pattern_provider.png`
- `textures/block/child_pattern_provider_alternate.png`
- `textures/block/child_pattern_provider_alternate_front.png`
- `textures/block/child_pattern_provider_alternate_arrow.png`
- `textures/item/sequence_pattern.png`
- `textures/item/provider_link.png`
- `textures/part/sequence_encoding_terminal.png`
- `textures/part/sequence_encoding_terminal_bright.png`
- `textures/part/sequence_encoding_terminal_medium.png`
- `textures/part/sequence_encoding_terminal_dark.png`

主控方块使用一张纹理覆盖六面。子供应器的方向模型与AE原版一致：模型默认输出方向为上方，方块状态文件负责旋转到六个实际方向。箭头纹理默认朝上绘制。

`reference/` 中的原版纹理只用于颜色、材质和像素密度参考，不应复制进最终发布资源。
