# GodVoice 1.0.1 - ONE jar

Build it once (GitHub Actions) -> you get ONE file: godvoice-1.0.1.jar

* SERVER (Paper 1.21.11):  put the jar in  plugins/   and restart.
* PLAYERS who want the big dark HUD box glued to the right of the screen: put the SAME jar in their
  .minecraft/mods/ folder (needs Fabric Loader + Fabric API for 1.21.11). The plugin detects it automatically.
* Players without the jar still get dialogue on the right-side sidebar.

## First-time setup (in game, as op)
/god grant <yourname> #9B30FF The Void
/god                      (lists every command)
/god model                become / stop being the god form
/god speak <player> <message...>

Box look (client): constants at the top of src/main/java/com/godvoice/client/DialogueOverlay.java
Model size: plugins/GodVoice/config.yml (model.scale)
