# Screen transitions

The workspace and settings now use a fixed-size 180 ms content fade with saved state per destination. Root navigation uses the same short fade. Navigation waits for the resumed lifecycle before opening a project or app, and guards duplicate navigation requests. Leaving a workspace defers its view-model reset until the outgoing fade ends. Root tab navigation opens the corresponding library rather than restoring a project stack. Consumed project-opening intents are cleared so recreation does not reopen a project after returning to a library.

The native runner initializes its screen and record state before restoring scroll, preventing a restored position from clamping against a temporarily empty list. Each screen retains its form and scroll state. Android's animator duration scale is respected.

The latest feature changes, concepts and verification are in [the October revision](../revision-20261005/README.md). The revised NavigationMotionTest verifies fixed outgoing bounds and restored native scrolling with animations enabled.
