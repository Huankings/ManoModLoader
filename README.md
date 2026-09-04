The only reason Claude is listed as a contributor is because of some chungus that used AI to fix a bug and i PR'd it. Fuck AI.

# HarpyModLoader

---
Modded role loader for the Harpy Express. 

## For players:

Start games with the horn, autostart or `/wathe:start harpymodloader:modded (select your own map entry)` to start a game with modded roles.

## For modders:

***Custom mods should use TMMRoles.registerRole() to add roles, then let this mod handle role assignment from there.***

This mod is **not** required be imported into any mod! Importing the mod into your project is only nessecary if wanting to add items when a player spawns, a role is removed *OR* if you want to add new Modifiers.

## What does the mod do?

---
### Roles
The mod makes it so roles added to TMMRoles.ROLES automatically have a RoleAnnouncement and are given to players. `canUseKillerFeatures()` will decide if the role is given to killers or civillians.

This mod also contains some client-side QOL features to make roles easier to see in spectator, with colored instinct glow and a role name-tag.

### Modifiers
Modifiers are a new sub-set of roles that players can get that can be randomly assigned to any player of any role (evil or not). Modders can decide which modifiers can be given to which roles, but by default; they can be assigned to every role.

Modifiers also show up on player name-tags in spectator or creative, but not on instinct.

## "Documentation"

---
not really documentation

### `/roleWeights` 调试指令说明

命令语法仍然使用英文关键字，方便已有脚本和管理员习惯继续使用；命令返回内容会根据客户端语言文件显示。中文客户端会显示中文字段，英文客户端会显示对应英文。

`/roleWeights list` 用于查看权重账本。`list all`（默认）同时显示在线玩家和有历史记录的离线玩家；`list online` 只显示在线玩家；`list stored` 只显示已保存账本。

列表中的 `实际获得/有效候选`（英文为 `actual/eligible`）不是一个分数，而是两个累计次数：

- `实际获得`：玩家最终被记录为该阵营或具体职业的局数。开局 `forceRole` 最终落地后会记录，局内 `/setRole` 不记录。
- `有效候选`：玩家实际进入该阵营或具体职业随机候选池的局数。具体职业只有在职业启用、有该职业槽位、且玩家进入该职业候选池时才增加；玩家从未进入某个职业池时不会被假设为“错过了很多局”。

例如 `杀手=3/20` 表示玩家实际当过 3 次杀手，且有 20 次进入杀手候选池的机会；`交换者=1/4` 表示实际当过 1 次交换者，只有 4 次进入交换者候选池。两者分别用于阵营公平和具体职业公平。

`/roleWeights preview faction <civilian|vigilante|killer|neutral>` 和 `/roleWeights preview role <role>` 用于预览当前概率，不会抽人、不写历史。它会同时显示两种概率：

- `全局概率`（`global probability`）：把当前服务器在线玩家全部视为本次开局池，使用预计阵营槽位或具体职业平均槽位计算。它回答“如果现在从全服在线玩家开始分配，这名玩家大约占多少概率”。
- `条件概率`（`conditional probability`）：只保留当前模式下已经属于目标阵营/具体职业候选条件的玩家。它回答“如果已经进入这个阵营/职业候选池，池内各玩家如何分配”。非进行中状态没有实际替换阶段，因此条件池会暂时回退为全部在线玩家。
- `权重`（`weight`）：抽签票数，不是百分比。单名玩家概率约等于其权重除以对应池内所有玩家权重总和。
- `实际获得` / `有效候选`（`actual` / `eligible`）：该玩家在当前目标阵营或职业上的累计实际次数和有效候选次数，用来解释权重为何偏高或偏低。
- `不可用`（`n/a`）：该玩家不在条件候选池，或当前条件池没有可计算槽位；这不表示玩家被永久禁用。

`/roleWeights enabled <true|false>` 是全服务器开关，保存在 Wathe 的全局 scoreboard；切换维度不会产生不同副本。关闭后历史仍保留，但自动抽取回到基础权重，重新启用即可继续使用历史。`reset` 会清除历史和调试覆盖，`clearOverride` 只清除手动覆盖、不清除实际历史。

### Installing the Library - Locally

This library does not have a Maven, so you will have to manually add it through a `files()` call in your `build.gradle`.

### Installing the Library - Modrinth Maven

I've never used the modrinth maven- but it seems you can use that to install the library. [Modrinth Maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven)

For reference, [here is the modrinth page to HarpyModLoader](https://modrinth.com/mod/harpymodloader)

### Run code when role is assigned/removed

To run code once a role is assigned, use the `ModdedRoleAssigned` event.

You can use the `ModdedRoleRemoved` event to check when a modded role is removed, but it is recommended to use `ResetPlayerEvent` if you are adding Attributes or other Persistent things on the player that can stick after a log-off, as `ResetPlayerEvent` runs right before a game starts, and after it ends.

Modifiers follow the same logic, with `ModifierAssigned` and `ModifierRemoved`.

### Adding a Modifier

Adding a modifier is as simple as a role.

Simply run the `HMLModifiers.registerModifier()` function and input a new Modifier to register it, with HML handling it. You can add exclusive/inclusive roles and whether the modifier is killer-bound or civillian-bound. For reference: Here's Tiny from Noelle's Roles: 

```java
public static Modifier TINY = HMLModifiers.registerModifier(new Modifier(TINY_ID, new Color(255, 223, 142).getRGB(), new ArrayList<>(List.of(MORPHLING)),null,false,false));
```

If you want to make 2-player Modifiers, You can do so inside ModifierAssigned; however I reccomend you check if the 2nd player already has a modifier to make sure the config's Modifier-Stacking is accounted for.

