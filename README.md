# SKILL SMP v1.2  (Paper plugin - Java + Bedrock through Geyser)

All-in-one server menu: compass menu, TPA, homes, warps, spawn, /back, kits, crates (% chances + keys), RTP,
economy (/pay /money /shop /sell /ah), bounties, daily reward, welcome / welcome back, auto announcements,
sidebar dashboard, leaderboards (money, kills, deaths, playtime) and a big in-game admin panel.
"SKILL SMP" is blue everywhere (change it in config.yml -> server-name).

## Update from v1.0
Replace the changed .java / .yml files, add the new Extras.java, build again, replace the jar.
New options and menu buttons are added to your existing config.yml automatically (your edits stay).
kits.yml / crates.yml / shop.yml are never overwritten.

## What is new in 1.2
- FIXED: the "random teleport" when tapping the compass on a phone. The finger that taps the compass is still on
  the screen when the menu opens and used to press a button (RTP / spawn). Menus now ignore taps in the first
  moments (config menus.click-delay, default 700 ms) and double taps.
- Small dashboard for phones (sidebar.compact), numbers like $1.2K / 3.4M, switch in Admin Panel -> Server tools.
- Auto lag clear every 5 minutes (dropped items + XP orbs) with warnings, /lagclear, Admin Panel button.
- Every list menu is the size of a shulker box (27 items per page). Homes: exactly 5 per player (homes.limit).
- Shop: 211 items in 10 categories. Crates: rarity (Common/Rare/Epic/Legendary), open x1 / x5 / x10,
  broadcast of big wins, rename crate, set icon.
- Many more settings in Admin Panel -> Settings.
- Bug fixes: balance overflow (economy.max-balance), chat prompts expire after 2 minutes, unknown shop materials
  are hidden instead of showing stone, compass check is lighter, leftover cooldown data is cleaned.
- config.yml / shop.yml are replaced by the new layout on first start (old ones saved as config-old.yml / shop-old.yml).

## What was new in 1.1
- Teleport fix: one teleport at a time, double taps ignored, async teleport (no more "teleports me again").
- Kits: whole inventory + armor + offhand (up to 41 items), armor auto-equips, preview menu, 45 kits per page,
  one-time kits, edit price/cooldown in the admin panel.
- Crates: every reward has a % chance (admin types 5 = 5%, others rebalance to 100%), command rewards,
  keys (virtual): key required or money, players can buy keys, admin can give keys, /crates keys.
- Welcome system: first-join and welcome-back (chat, title, sound, bonus money, auto kit), join/quit messages,
  all with color codes. Placeholders: %player% %server% %number% %away% ...
- New: /warp /spawn /back /bounty /daily /broadcast, playtime tracking + leaderboard, auto announcer.
- Admin panel: Players tools (heal, gamemode, fly, kick, tp, reset stats), Server tools (day/night, weather,
  clear dropped items, sidebar, announcer), Warps editor, Crates keys/percent editor, Settings for welcome etc.
- Lightweight: leaderboard leaders cached 30s, sidebar only rewrites changed lines, data saved every 5 min.

## Requirements
- Paper 26.1+ (Java 25)  -  Geyser + Floodgate if you want Bedrock players
- To build: JDK 25 + Maven (or GitHub Actions, see build.yml)

## Admin
- /op <you> or /tag <you> add owner ; give others the panel with  /tag <player> add admin
- Compass -> Admin Panel (or /adminpanel)

## Commands
/menu  /tpa  /tpahere  /tpaccept  /tpdeny  /tpcancel  /sethome  /home  /delhome  /homes  /back
/warp [name]  /spawn  /setspawn  /setwarp <name>  /delwarp <name>
/kits  /kit <name>  /crates  /crates keys  /crates givekey <player> <crate> <amount>
/rtp  /money [player] (/bal)  /pay  /shop  /sell [hand|all]  /ah  /ah sell <price>  /top
/bounty [player amount]  /daily  /economy <give|take|set|reset> <player> [amount]
/adminpanel  /broadcast <msg>  /skillsmp reload  /skillsmp compass <player>

## Permissions
skillsmp.admin, skillsmp.owner, skillsmp.bypass.cooldown, skillsmp.bypass.warmup,
skillsmp.homes.unlimited, skillsmp.homes.<number>

## Bedrock notes
- Menus are chest GUIs, so Bedrock players tap through Geyser. Admin questions are answered in normal chat (cancel to abort).
- TPA: Bedrock can't tap chat links - use /tpaccept or compass -> TPA -> Incoming requests, or set tpa.auto-open-menu: true.

## Known limits
- Not tested on a live server by the author of this file - report any bug and it will be fixed.
- No Vault bridge, auction listings don't expire, crate keys are virtual (not items), /pay is for online players.
