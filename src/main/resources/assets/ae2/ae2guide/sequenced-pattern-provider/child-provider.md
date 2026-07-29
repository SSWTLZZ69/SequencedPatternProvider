---
navigation:
  parent: sequenced-pattern-provider/index.md
  title: Child Provider
  icon: sequenced_pattern_provider:child_pattern_provider
  position: 30
item_ids:
  - sequenced_pattern_provider:child_pattern_provider
---

# Child Provider

The child sends one assembly step to the machine in front of it. Its front face is the output; AE cables connect to the other faces.

For a child to work, it must be on the master's AE network, linked to that master, and assigned the same route marker used by the pattern.

## Linking to a Master

1. **Shift-right-click the master** with an <ItemLink id="ae2:memory_card" />.
2. Release Shift and **normally right-click the child provider** with the same card.
3. Open the master and confirm that its connected-child count increased.

Shift-right-clicking the child is not a link action. It saves that child's settings instead.

## Assigning Processing Types

Hold an item that represents this processing line and normally right-click the child to assign it as a route marker. Put the same item in the corresponding step's Route Marker slot in the encoding terminal. A marker only chooses a child; it is not sent to the machine or consumed.

One child may use several markers. This can make several steps share one processing line or distinguish different lines that use similar machines.

Empty-hand right-click opens the GUI. It shows the assigned types, AE connection, front target and temporary buffers. Use **Clear** to remove all assigned types.

## Copying Settings

Shift-right-click a child with a memory card to save its route markers, blocking mode, crafting lock mode, output direction and name. Normally right-click another child to apply them. Buffered items, fluids and current crafting locks are not copied.

## Multiple Providers for One Type

You may link several child providers that handle the same processing type. The master rotates between them. If one child is offline, blocked, still has buffered output, or its front target refuses the batch, the master tries the next matching child. A job waits only when every matching child is unavailable.

## Blocking Mode

The button on the left follows AE2's pattern-provider blocking mode.

- **Off:** the child may send another batch whenever the front target accepts it.
- **On:** the child does not send another batch while the front target still contains an item or fluid used by any encoded step that this child can process.

Blocking mode checks materials from every pattern handled by this child, not only the batch currently being sent. It only checks inventory visible through the front target and does not wait for an intermediate to return.

## Crafting Lock

The second button on the left is the same crafting-lock selector used by an AE2 Pattern Provider. It is separate from blocking mode.

- **Until redstone pulse:** locks after sending a batch, then unlocks on the next complete redstone pulse.
- **While signal is high / low:** dynamically prevents new batches while the selected signal state is present.
- **Until result returns:** locks after sending a batch and unlocks after that step's expected intermediate or final result enters the AE network. The result may return through the child or through another network input.

Changing the lock mode clears the current lock. Aborting all jobs at the master also releases result locks owned by those jobs.

Finished intermediates may return through this child, an Import Bus, an Interface, or any other route into the same AE network as the master.
