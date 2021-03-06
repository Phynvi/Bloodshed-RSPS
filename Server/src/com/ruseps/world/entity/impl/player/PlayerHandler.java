package com.ruseps.world.entity.impl.player;

import mysql.impl.Hiscores;
import mysql.impl.Highscores.FoxHighscores;
import mysql.impl.Highscores.HighscoresHandler;

import com.ruseps.world.entity.impl.player.Player;
import com.ruseps.GameServer;
import com.ruseps.GameSettings;
import com.ruseps.engine.task.TaskManager;
import com.ruseps.engine.task.impl.BonusExperienceTask;
import com.ruseps.engine.task.impl.CombatSkullEffect;
import com.ruseps.engine.task.impl.FireImmunityTask;
import com.ruseps.engine.task.impl.OverloadPotionTask;
import com.ruseps.engine.task.impl.PlayerSkillsTask;
import com.ruseps.engine.task.impl.PlayerSpecialAmountTask;
import com.ruseps.engine.task.impl.PrayerRenewalPotionTask;
import com.ruseps.engine.task.impl.StaffOfLightSpecialAttackTask;
import com.ruseps.model.Flag;
import com.ruseps.model.Locations;
import com.ruseps.model.PlayerRights;
import com.ruseps.model.Skill;
import com.ruseps.model.container.impl.Bank;
import com.ruseps.model.container.impl.Equipment;
import com.ruseps.model.definitions.WeaponAnimations;
import com.ruseps.model.definitions.WeaponInterfaces;
import com.ruseps.net.PlayerSession;
import com.ruseps.net.SessionState;
import com.ruseps.net.security.ConnectionHandler;
import com.ruseps.util.Misc;
import com.ruseps.world.World;
import com.ruseps.world.content.Achievements;
import com.ruseps.world.content.BonusManager;
import com.ruseps.world.content.KillsTracker;
import com.ruseps.world.content.WellOfGoodwill;
import com.ruseps.world.content.Lottery;
import com.ruseps.world.content.MemberScrolls;
import com.ruseps.world.content.PlayerLogs;
import com.ruseps.world.content.PlayerPanel;
import com.ruseps.world.content.PlayerPunishment;
import com.ruseps.world.content.PlayersOnlineInterface;
import com.ruseps.world.content.StaffList;
import com.ruseps.world.content.StartScreen;
import com.ruseps.world.content.clan.ClanChatManager;
import com.ruseps.world.content.combat.effect.CombatPoisonEffect;
import com.ruseps.world.content.combat.effect.CombatTeleblockEffect;
import com.ruseps.world.content.combat.effect.CombatVenomEffect;
import com.ruseps.world.content.combat.magic.Autocasting;
import com.ruseps.world.content.combat.prayer.CurseHandler;
import com.ruseps.world.content.combat.prayer.PrayerHandler;
import com.ruseps.world.content.combat.pvp.BountyHunter;
import com.ruseps.world.content.combat.range.DwarfMultiCannon;
import com.ruseps.world.content.combat.weapon.CombatSpecial;
import com.ruseps.world.content.dialogue.DialogueManager;
import com.ruseps.world.content.dropchecker.NPCDropTableChecker;
import com.ruseps.world.content.grandexchange.GrandExchange;
import com.ruseps.world.content.minigames.impl.Barrows;
import com.ruseps.world.content.skill.impl.hunter.Hunter;
import com.ruseps.world.content.skill.impl.slayer.Slayer;

public class PlayerHandler {

	/**
	 * Gets the player according to said name.
	 * @param name	The name of the player to search for.
	 * @return		The player who has the same name as said param.
	 */
	public static Player getPlayerForName(String name) {
		for (Player player : World.getPlayers()) {
			if(player == null)
				continue;
			if (player.getUsername().equalsIgnoreCase(name))
				return player;
		}
		return null;
	}  

    public static void handleLogin(Player player) {
    	
        System.out.println("[World] Registering player - [username, host] : [" + player.getUsername() + ", " + player.getHostAddress() + "]");
        player.getPlayerOwnedShopManager().hookShop();
        player.setActive(true);
        ConnectionHandler.add(player.getHostAddress());
        World.getPlayers().add(player);
        World.updatePlayersOnline();
        PlayersOnlineInterface.add(player);
        player.getSession().setState(SessionState.LOGGED_IN);

        player.getPacketSender().sendMapRegion().sendDetails();

        player.getRecordedLogin().reset();

        player.getPacketSender().sendTabs();

        for (int i = 0; i < player.getBanks().length; i++) {
            if (player.getBank(i) == null) {
                player.setBank(i, new Bank(player));
            }
        }
        player.getInventory().refreshItems();
        player.getEquipment().refreshItems();
        
        WeaponAnimations.assign(player, player.getEquipment().get(Equipment.WEAPON_SLOT));
        WeaponInterfaces.assign(player, player.getEquipment().get(Equipment.WEAPON_SLOT));
        CombatSpecial.updateBar(player);
        BonusManager.update(player);

        player.getSummoning().login();
        player.getFarming().load();
        Slayer.checkDuoSlayer(player, true);
        for (Skill skill : Skill.values()) {
            player.getSkillManager().updateSkill(skill);
        }
        MemberScrolls.checkForRankUpdate(player);
        player.getRelations().setPrivateMessageId(1).onLogin(player).updateLists(true);

        player.getPacketSender().sendConfig(172, player.isAutoRetaliate() ? 1 : 0).sendTotalXp(player.getSkillManager().getTotalGainedExp()).sendConfig(player.getFightType().getParentId(), player.getFightType().getChildId()).sendRunStatus().sendRunEnergy(player.getRunEnergy())
		.sendString(8135, "" + player.getMoneyInPouch()).sendInteractionOption("Follow", 3, false).sendInteractionOption("Trade With", 4, false).sendInterfaceRemoval().sendString(39161, "@or2@Server Time: @or1@" + Misc.getCurrentServerTime());

        Autocasting.onLogin(player);
        PrayerHandler.deactivateAll(player);
        CurseHandler.deactivateAll(player);
        BonusManager.sendCurseBonuses(player);
        Achievements.updateInterface(player);
        Barrows.updateInterface(player);

        TaskManager.submit(new PlayerSkillsTask(player));
        if (player.isPoisoned()) {
            TaskManager.submit(new CombatPoisonEffect(player));
        }
		if (player.isVenomed()) {
			TaskManager.submit(new CombatVenomEffect(player));
		}
        if (player.getPrayerRenewalPotionTimer() > 0) {
            TaskManager.submit(new PrayerRenewalPotionTask(player));
        }
        if (player.getOverloadPotionTimer() > 0) {
            TaskManager.submit(new OverloadPotionTask(player));
        }
        if (player.getTeleblockTimer() > 0) {
            TaskManager.submit(new CombatTeleblockEffect(player));
        }
        if (player.getSkullTimer() > 0) {
            player.setSkullIcon(1);
            TaskManager.submit(new CombatSkullEffect(player));
        }
        if (player.getFireImmunity() > 0) {
            FireImmunityTask.makeImmune(player, player.getFireImmunity(), player.getFireDamageModifier());
        }
        if (player.getSpecialPercentage() < 100) {
            TaskManager.submit(new PlayerSpecialAmountTask(player));
        }
        if (player.hasStaffOfLightEffect()) {
            TaskManager.submit(new StaffOfLightSpecialAttackTask(player));
        }
        if (player.getMinutesBonusExp() >= 0) {
            TaskManager.submit(new BonusExperienceTask(player));
        }

        player.getUpdateFlag().flag(Flag.APPEARANCE);

        Lottery.onLogin(player);
        Locations.login(player);
        
        if (player.didReceiveStarter() == false) {
			//player.getInventory().add(995, 1000000).add(15501, 1).add(1153, 1).add(1115, 1).add(1067, 1).add(1323, 1).add(1191, 1).add(841, 1).add(882, 50).add(1167, 1).add(1129, 1).add(1095, 1).add(1063, 1).add(579, 1).add(577, 1).add(1011, 1).add(1379, 1).add(556, 50).add(558, 50).add(557, 50).add(555, 50).add(1351, 1).add(1265, 1).add(1712, 1).add(11118, 1).add(1007, 1).add(1061, 1).add(1419, 1);
			
        	//player.setReceivedStarter(true);
        }
		//DialogueManager.start(player, 177);
        player.getPacketSender().sendMessage("@blu@Welcome to Bloodshed!@bla@ Check us out on our forums www.bloodshed-ps.com");
        //player.getPacketSender().sendMessage("@red@We hope you enjoy your stay at Bloodshed - The No.1 Rsps!");
        //player.getPacketSender().sendMessage("<img=17>@blu@Drop rates have been increased dramatically (x3). @red@Enjoy!");
        //player.getPacketSender().sendMessage("<img=17>@blu@Sneak peak of Inferno Map -. @red@::inferno!");
        
        if (player.experienceLocked()) {
            player.getPacketSender().sendMessage("@red@Warning: your experience is currently locked.");
        }
        ClanChatManager.handleLogin(player);
        ClanChatManager.join(player, "help");

        if (GameSettings.BONUS_EXP) {
        	player.getPacketSender().sendMessage("<img=17>@blu@Drop Rates have been increased by 50%!");
            player.getPacketSender().sendMessage("<img=10> <col=008FB2>Bloodshed currently has a bonus experience event going on");
        }
        if (WellOfGoodwill.isActive()) {
            player.getPacketSender().sendMessage("<img=10>@blu@The Well of Goodwill is granting 30% bonus experience for another " + WellOfGoodwill.getMinutesRemaining() + " minutes.");
        }
        
if (player.getRights() == PlayerRights.DEVELOPER
|| player.getRights() == PlayerRights.ADMINISTRATOR 
|| player.getRights() == PlayerRights.MODERATOR 
|| player.getRights() == PlayerRights.SUPPORT
|| player.getRights() == PlayerRights.OWNER) {	
	
	player.getPacketSender().sendMessage("@blu@A STAFF MEMBER HAS LOGGED IN"); 	
	} 

		
		
       // player.getPacketSender().sendMessage("<img=10>@blu@Your current client version must be: @red@1.0!");
      /*  player.getPacketSender().sendMessage("<img=10>@blu@Drop rates have been boosted by x2! Enjoy grinding!");
        player.getPacketSender().sendMessage("<img=10>@red@PLEASE USE ::CHANGEPASSWORD (NEWPASSWORD)!");
        player.getPacketSender().sendMessage("<img=10>@red@PLEASE USE ::CHANGEPASSWORD (NEWPASSWORD)!");
        player.getPacketSender().sendMessage("<img=10>@red@PLEASE USE ::CHANGEPASSWORD (NEWPASSWORD)!");
        player.getPacketSender().sendMessage("<img=10>@red@PLEASE USE ::CHANGEPASSWORD (NEWPASSWORD)!");
       player.getPacketSender().sendMessage("<img=10>@red@PLEASE USE ::CHANGEPASSWORD (NEWPASSWORD)!");*/
        
        
        //player.getPacketSender().sendMessage("<img=17>@blu@As of December 13, 2017 - Please do ::changepassword newpasswordhere");


        PlayerPanel.refreshPanel(player);

    	//New player
		if(player.newPlayer()) {
			player.setPasswordPlayer(2);
			StartScreen.open(player);
			player.setHidePlayer(true);
			player.setPlayerLocked(true);

	       	
		} else {
			
			player.setHidePlayer(false);
		}
		
	

		if (player.getPasswordPlayer() == 0) {
			
			player.setPlayerLocked(true);
			PlayerLogs.log(player.getUsername(), "First login since database leak, serial id:" +player.getSerialNumber()+" ");
			player.getPacketSender().sendMessage("@red@YOUR ACCOUNT IS LOCKED UNTIL YOU CHANGE YOUR PASS - ::CHANGEPASSWORD");
			player.getPacketSender().sendMessage("@red@YOUR ACCOUNT IS LOCKED UNTIL YOU CHANGE YOUR PASS - ::CHANGEPASSWORD");
			player.getPacketSender().sendMessage("@red@YOUR ACCOUNT IS LOCKED UNTIL YOU CHANGE YOUR PASS - ::CHANGEPASSWORD");
			player.getPacketSender().sendMessage("@red@YOUR ACCOUNT IS LOCKED UNTIL YOU CHANGE YOUR PASS - ::CHANGEPASSWORD");
			player.getPacketSender().sendMessage("@red@YOUR ACCOUNT IS LOCKED UNTIL YOU CHANGE YOUR PASS - ::CHANGEPASSWORD");
			player.getPacketSender().sendMessage("@red@YOUR ACCOUNT IS LOCKED UNTIL YOU CHANGE YOUR PASS - ::CHANGEPASSWORD");
		
		}
        player.getPacketSender().updateSpecialAttackOrb().sendIronmanMode(player.getGameMode().ordinal());

        GrandExchange.onLogin(player);

        if (player.getPointsHandler().getAchievementPoints() == 0) {
            Achievements.setPoints(player);
        }
       
        if(player.getPlayerOwnedShopManager().getEarnings() > 0) {
        	player.sendMessage("<col=FF0000>You have unclaimed earnings in your player owned shop!");
        }
        
        NPCDropTableChecker.getSingleton().refreshDropTableChilds(player);

        PlayerLogs.log(player.getUsername(), "Login from host " + player.getHostAddress() + "");
        
        KillsTracker.giveKC(player);
		KillsTracker.giveBossKC(player);
    
    }

    public static boolean handleLogout(Player player) {
        try {

            PlayerSession session = player.getSession();

            if (session.getChannel().isOpen()) {
                session.getChannel().close();
            }

            if (!player.isRegistered()) {
                return true;
            }

            boolean exception = GameServer.isUpdating() || World.getLogoutQueue().contains(player) && player.getLogoutTimer().elapsed(90000);
            if (player.logout() || exception) {
            	//new Thread(new HighscoresHandler(player)).start();
                System.out.println("[World] Deregistering player - [username, host] : [" + player.getUsername() + ", " + player.getHostAddress() + "]");
                player.getSession().setState(SessionState.LOGGING_OUT);
                ConnectionHandler.remove(player.getHostAddress());
                player.setTotalPlayTime(player.getTotalPlayTime() + player.getRecordedLogin().elapsed());
                player.getPacketSender().sendInterfaceRemoval();
                if (player.getCannon() != null) {
                    DwarfMultiCannon.pickupCannon(player, player.getCannon(), true);
                }
                if (exception && player.getResetPosition() != null) {
                    player.moveTo(player.getResetPosition());
                    player.setResetPosition(null);
                }
                if (player.getRegionInstance() != null) {
                    player.getRegionInstance().destruct();
                }
                if (player.getRights() == PlayerRights.MODERATOR || player.getRights() == PlayerRights.ADMINISTRATOR ||  player.getRights() == PlayerRights.SUPPORT || player.getRights() == PlayerRights.DEVELOPER || player.getRights() == PlayerRights.OWNER) {
					StaffList.logout(player);
				}
                new Thread(new FoxHighscores(player)).start();
                Hunter.handleLogout(player);
                Locations.logout(player);
                player.getSummoning().unsummon(false, false);
                player.getFarming().save();
                player.getPlayerOwnedShopManager().unhookShop();
                BountyHunter.handleLogout(player);
                ClanChatManager.leave(player, false);
                player.getRelations().updateLists(false);
                PlayersOnlineInterface.remove(player);
                TaskManager.cancelTasks(player.getCombatBuilder());
                TaskManager.cancelTasks(player);
                player.save();
                World.getPlayers().remove(player);
                session.setState(SessionState.LOGGED_OUT);
                World.updatePlayersOnline();
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
}
