---
navigation:
  parent: sequenced-pattern-provider/index.md
  title: Setup and Troubleshooting
  icon: ae2:memory_card
  position: 40
---

# Setup and Troubleshooting

## Linking the Master and Children

Both the AE network connection and the memory-card link are required:

1. Put the master and children on the same powered AE network with available channels. Point each child's front face at its machine and connect AE cable to another face.
2. Hold an <ItemLink id="ae2:memory_card" /> and **shift-right-click the master** to save it.
3. Release Shift and **normally right-click each child provider** with the same card.
4. Open the master. Its connected-child count increases after every successful link.

If the count does not increase, make sure Shift is released when using the card on the child. Shift-right-clicking a child saves its settings and does not link it to the master.

The card keeps the saved master so several children can be linked in succession. Shift-right-click the air to clear the card. Use **Clear Links** in the master to remove all children linked to that master.

The same child may be linked to more than one master. Record each master in turn and normally right-click the shared child with that master's card. All participating masters and the shared child must remain on the same AE network; the child is one shared processing line, including its buffer and crafting lock.

## Copying a Master's Child Links

When adding another master with the same production lines, you do not need to link every child again:

1. Hold a memory card and **shift-right-click the old master**.
2. Release Shift and **normally right-click the new master**.
3. The new master merges the saved child links. Existing links are kept; patterns, jobs, buffers and locks are not copied.

The card still retains the old master's link profile, so it can also be used to normally right-click individual children. Re-record the new master on the card when you want to add more children to the new master one by one. Links from unloaded chunks are deferred and validated when the chunk is loaded.

## The Result Is Not Craftable

Check these points:

- The encoded pattern is inside the master.
- The master and children are powered and have channels, and the master's connected-child count is correct.
- Every route marker in the pattern is assigned to at least one linked child.
- The configured job limit is not already full.

## A Job Stops at One Step

- Check the child whose processing type matches that step.
- Check that its front face points toward a valid input.
- Check machine power, Create stress, heat and available space.
- If blocking mode is on, remove leftover input materials from the front target.
- Make sure the finished intermediate returns to the master's AE network.

If the factory cannot recover, open the master and use **Abort All**. This only returns materials that have not entered the factory.

## Copying Child Settings

Shift-right-click a child with a memory card to save its route markers, blocking mode, crafting lock mode, output direction and name. Normally right-click another child to apply them. A card stores one kind of data at a time; shift-right-click the master again before linking more children.

## Naming Devices

Use AE2's normal naming workflow: make a name press with a quartz cutting knife, use the Inscriber to name the master, child or encoding-terminal item, then place it. Names only help identify devices; they do not replace memory-card links or route markers.
