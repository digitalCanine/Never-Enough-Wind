To do :

Item class :

    Item parser (mainly a getter, used for sending item info to other method below)

    Item cattegory parser

    God item identifier (keeps original colors if item is auc or jesters [jesters might not work proprely])

    Essence parser

    Item essence tooltips parser (implementing the parser above)

    Essence tooltips parser

    True damage algo (Takes sharpness level and essences applied and shows the real average damage)

    Item Value parser (using debugmenu prices, uses the item essence parser to estimate value of item with essences on it)


Server info class :

    Scoreboard parser (including party specific scoreboard)

    World / hashed seed parser

    Event parser

    Fresh blood timer parser (if possible, otherwise hardcode timer using chat filter)

    Daily guaranteed wild key parser (If possible, might not be at all)


Chat class :

    Clan tag parset

    Windchat fork (use codebase as a base to build a chat extension)

    Chat regex and filters


Player class (only on self):

    Get mw specific buffs (Bloodlust cooldown or antimage for example)

    Get mw specific debuffs (For things like md, feed block, or cripple cooldowns)
    

UI class (the meat) :

    Event reminder (use event parser)

    Tooltips display (use all the item parsers and getters and build cases)

    Party members widget (use scoreboard parser)

    Own score (use scoreboard parser)

    Subserver indicator (use world parser)

    Timers (Command showing timer for fresh blood, wild key, dailies, weeklies, etc, using the parsers if possible)

    Player clantags (using clantag parser, draw clantag next/on top of of player nametags)

    Item Highligter (using item cattegory parser)

    Essence menu (uses the essence list json to show every essence in the game as a menu)

    Buffs/Debuff timer widget (if possible, parsing from the player class methods)
    
