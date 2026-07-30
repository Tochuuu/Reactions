# Changelog

## 0.7.1

### Added
- Vehicle back-look eye reaction while riding boats and other vehicles.
- Eye focus reactions for interactive block screens, including crafting-table-style function blocks.
- Reading eye movement for books and sign editing screens.
- Falling surprise and landing blink eye reactions.
- Support for larger eye layouts up to `3x2`; `3x3` eyes remain blocked.
- Fabric server relay support for cross-loader face config and eye focus syncing.

### Changed
- Updated mod version to `0.7.1`.
- Improved eyelid rendering for larger eye layouts.
- Tuned block focus tolerance so eyes return to neutral when the player faces the block directly.
- Disabled tint overlay rendering globally for face overlays when clean eyelid color is enabled.

### Fixed
- NeoForge clients can now see Fabric player face config and eye focus when connected through the Fabric server relay.
- Vehicle back-look eye direction now follows the player's head direction while riding.
- Block focus eye direction now handles left, right, up, and down cases more consistently.
- Larger eye focus animations now move the intended pupil pixels instead of only the lower row.
- Pupil UV placement was adjusted to avoid sclera-edge glitches.
