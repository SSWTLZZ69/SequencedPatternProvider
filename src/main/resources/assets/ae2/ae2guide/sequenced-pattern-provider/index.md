---
navigation:
  title: Sequenced Pattern Provider
  icon: sequenced_pattern_provider:master_pattern_provider
  position: 60
---

# Sequenced Pattern Provider

This addon lets AE2 order a complete Create sequenced assembly recipe as one craft. You no longer need a separate processing pattern for every step and loop.

You need three things:

- A [pattern encoding terminal](encoder.md) to make the special pattern.
- A [master provider](master-provider.md) to hold patterns and accept AE crafting orders.
- One or more [child providers](child-provider.md) to feed the Create machines.

## Quick Setup

1. Put the master and child providers on the same powered AE network. Point each child's front face at its machine or input inventory.
2. Use JEI's **+** button in the encoding terminal, check every material and route marker, then encode the pattern.
3. For every route marker in the pattern, hold the same item and **normally right-click** the child responsible for that processing line.
4. Hold an <ItemLink id="ae2:memory_card" /> and **shift-right-click the master** to save it.
5. Release Shift and **normally right-click each child provider** with the same card. The master's connected-child count increases after every successful link.
6. Put the pattern in the master and request the final item from AE.

To add another nine-pattern master for the same production lines, shift-right-click the existing master with a memory card, then normally right-click the new master to copy its child-link set.

Do not shift-right-click a child while linking. Shift-right-clicking a child saves its settings for copying; it does not link the child to the master.

See [Setup and Troubleshooting](workflow.md) if the item cannot be ordered or a job stops.
