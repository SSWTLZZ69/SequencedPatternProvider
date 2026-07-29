---
navigation:
  parent: sequenced-pattern-provider/index.md
  title: Master Provider
  icon: sequenced_pattern_provider:master_pattern_provider
  position: 20
item_ids:
  - sequenced_pattern_provider:master_pattern_provider
---

# Master Provider

The master is the part AE2 sees when planning a craft. It holds up to nine sequenced assembly patterns and requires a channel.

## Linking Child Providers

After connecting the blocks with AE cable, use a memory card to link them:

1. Hold an <ItemLink id="ae2:memory_card" /> and **shift-right-click the master** to save it.
2. Release Shift and **normally right-click a child provider** with the same card.
3. Normally right-click more children to link all of them to the same master.
4. Open the master and confirm that its connected-child count increased.

This link does not replace AE cabling. The master and every child must still be on the same powered AE network with available channels.

Do not shift-right-click a child while linking. That action saves the child's settings for copying instead.

Before AE shows a result as craftable, every processing type in its pattern must have an online child provider connected to this master.

The GUI shows:

- Installed patterns.
- Connected child providers.
- Running jobs and the configured job limit.
- **Abort All**, which stops all jobs and returns materials the master has not sent yet.

Items already inside machines cannot be pulled back by Abort All.

After a machine finishes, return the intermediate to the master's AE network and the master will send its next step. The item may return through a child provider, Import Bus, Interface, or any other network input.

New orders wait when the running-job limit is full. The server configuration controls this limit.
