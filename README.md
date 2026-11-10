# Minecraft Paper Plugin: Thornhaven Death Animation

Thornhaven Death Animation is a lightweight Minecraft Paper plugin that adds configurable death and respawn animations, particles, gradient-colored titles, and sounds without NMS or a resource pack.

## Install

1. Build the project with Java 25 and Gradle by running `gradle build`.
2. Copy `build/libs/ThornhavenDeathAnimation.jar` into your server’s `plugins` folder.
3. Start the server to generate `plugins/ThornhavenDeathAnimation/config.yml`.

## Use

- When a player takes lethal damage, the plugin cancels that damage, gradually raises them about 2 blocks over 20 ticks, shows the configured death title and subtitle, plays the configured sound, and emits soul fire flame and smoke particles.
- After the death animation, the player is killed and the normal Minecraft death screen appears. The player must use the usual respawn control; automatic respawn and suppression of the death screen are not implemented.
- On respawn, when the path above the destination is clear, the player starts up to 5 blocks above it and descends over 40 ticks with a moving circle of end rod particles. The plugin shows the configured respawn title and plays its sound. Fall damage is suppressed during this animation.
- Edit `config.yml` to adjust titles, subtitles, gradient colors, particles, sounds, durations, heights, particle radius and count, volume, and pitch. Run `/tda reload` to reload the configuration. The command requires `thornhaven.animation.reload`, granted to operators by default.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/4d576dee-3bf3-41c9-8e3c-63beddd804fb?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=4d576dee-3bf3-41c9-8e3c-63beddd804fb
