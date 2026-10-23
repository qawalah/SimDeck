-- SimDeck: loads the game engine extension when the mod is active.
-- The extension loads its vehicle part into the player vehicle by itself.
load("simdeckLink")
setExtensionUnloadMode("simdeckLink", "manual")
