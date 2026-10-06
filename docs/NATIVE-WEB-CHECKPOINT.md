# Native/Web rollback checkpoint — 2026-10-06

Before the shared-descriptor experiment, the complete implementation is committed
at `526c4fa` (`fix: confirm HID teardown and fresh reconnect on mode switches`).
Native/Web use the original 237-byte descriptor with X/Y/Z/Rx axes. Android uses
X/Y/Z/Rz. Report mappings and all current diagnostics are included in that commit.

This checkpoint preserves the implementation, not a claim that live descriptor
switching works: user testing confirms Android receivers can retain the previous
descriptor until the devices are forgotten and paired again.

To inspect or build the checkpoint without discarding current work, create a
separate checkout: `git worktree add --detach ../Bluke-native-web-checkpoint 526c4fa`.
Do not reset a dirty working tree or rewrite the remote branch to roll back.

Existing packaged build: `app/build/outputs/apk/debug/bluke-1.1-descriptor-switch-fix.apk`.
SHA-256: `40C4963280E8C7FAC19C758236E41F87C5F430E2F488369D9617670274DB7A42`.
