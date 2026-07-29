---
navigation:
  parent: sequenced-pattern-provider/index.md
  title: Pattern Encoding Terminal
  icon: sequenced_pattern_provider:sequence_encoding_terminal
  position: 10
item_ids:
  - sequenced_pattern_provider:sequence_encoding_terminal
  - sequenced_pattern_provider:sequence_pattern
---

# Pattern Encoding Terminal

Place the terminal on an AE cable and open it.

The easiest way to encode a recipe is:

1. Open a Create sequenced assembly recipe in JEI.
2. Click JEI's **+** button while this terminal is open.
3. Check the starting item, result, steps and loop count.
4. Insert an AE2 blank pattern and press the encode arrow.

The encoded result remains a dedicated sequenced assembly pattern so AE2 can distinguish and decode its extra step data. Shift-right-click it in your hand, or use the terminal's clear button, to turn it back into an AE2 blank pattern.

Each step has two slots:

- **Material:** an extra item or fluid consumed during that step. Leave it empty when the step needs nothing extra.
- **Route marker:** any item used to choose a child provider. It identifies the destination black box and is not sent as recipe material.

After encoding, normally right-click the responsible child with each step's route-marker item. The master can send a step only when a linked child has the same marker as the pattern.

The terminal supports up to ten steps in one loop. Power, heat, stress and other machine requirements are supplied by the factory itself; they are not pattern slots unless the recipe exposes them as an item or fluid.

## Probabilistic results

When a probabilistic result is selected, the terminal groups several attempts into one batch and converts the recipe chance into the amount planned by AE. For example, an 80% precision-mechanism result becomes **5 attempts -> 4 precision mechanisms**. Other results and target results above the planned amount also enter the network.

One batch may use several machines assigned to the same processing line. Their completion order does not stop the master from continuing later steps. If one attempt produces no item, the master continues processing the other workpieces in the batch.
