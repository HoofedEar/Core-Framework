package com.openrsc.server.plugins.skills.smithing;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.event.custom.BatchEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

import static com.openrsc.server.plugins.Functions.*;

public class Smelting implements UseLocTrigger {

	public static final int FURNACE = 118;
	public static final int LAVA_FURNACE = 1284;

	@Override
	public void onUseLoc(GameObject obj, Item item, Player player) {
		if (obj.getID() == FURNACE && !DataConversions.inArray(new int[]{ItemId.GOLD_BAR.id(), ItemId.SILVER_BAR.id(), ItemId.SAND.id(), ItemId.GOLD_BAR_FAMILYCREST.id()}, item.getCatalogId())) {
			if (item.getCatalogId() == ItemId.STEEL_BAR.id()) {
				if (player.getCarriedItems().hasCatalogID(ItemId.CANNON_AMMO_MOULD.id())) {
					if (getCurrentLevel(player, Skills.SMITHING) < 30) {
						player.message("You need at least level 30 smithing to make cannon balls");
						return;
					}
					if (player.getQuestStage(Quests.DWARF_CANNON) != -1) {
						player.message("You need to complete the dwarf cannon quest");
						return;
					}
					thinkbubble(player, new Item(ItemId.MULTI_CANNON_BALL.id(), 1));
					int messagedelay = player.getWorld().getServer().getConfig().BATCH_PROGRESSION ? 200 : 1700;
					int delay = player.getWorld().getServer().getConfig().BATCH_PROGRESSION ? 7200: 2100;
					mes(player, messagedelay, "you heat the steel bar into a liquid state",
						"and pour it into your cannon ball mould",
						"you then leave it to cool for a short while");

					player.setBatchEvent(new BatchEvent(player.getWorld(), player, delay, "Smelting", player.getCarriedItems().getInventory().countId(item.getCatalogId()), false) {
						@Override
						public void action() {
							getOwner().incExp(Skills.SMITHING, 100, true);
							getOwner().getCarriedItems().getInventory().replace(ItemId.STEEL_BAR.id(), ItemId.MULTI_CANNON_BALL.id(),false);
							if (getOwner().getCarriedItems().getEquipment().hasEquipped(ItemId.DWARVEN_RING.id())) {
								getOwner().getCarriedItems().getInventory().add(new Item(ItemId.MULTI_CANNON_BALL.id(), getWorld().getServer().getConfig().DWARVEN_RING_BONUS),false);
								int charges;
								if (getOwner().getCache().hasKey("dwarvenring")) {
									charges = getOwner().getCache().getInt("dwarvenring") + 1;
									if (charges >= getWorld().getServer().getConfig().DWARVEN_RING_USES) {
										getOwner().getCache().remove("dwarvenring");
										getOwner().getCarriedItems().getInventory().shatter(ItemId.DWARVEN_RING.id());
									} else
										getOwner().getCache().put("dwarvenring", charges);
								}
								else
									getOwner().getCache().put("dwarvenring", 1);

							}
							ActionSender.sendInventory(getOwner());
							getOwner().message("it's very heavy");

							if (!isCompleted()) {
								getOwner().message("you repeat the process");
								thinkbubble(getOwner(), new Item(ItemId.MULTI_CANNON_BALL.id(), 1));
							}
							if (getCurrentLevel(getOwner(), Skills.SMITHING) < 30) {
								getOwner().message("You need at least level 30 smithing to make cannon balls");
								interrupt();
								return;
							}
							if (getOwner().getQuestStage(Quests.DWARF_CANNON) != -1) {
								getOwner().message("You need to complete the dwarf cannon quest");
								interrupt();
								return;
							}
							if (getWorld().getServer().getConfig().WANT_FATIGUE) {
								if (getWorld().getServer().getConfig().STOP_SKILLING_FATIGUED >= 2
									&& getOwner().getFatigue() >= getOwner().MAX_FATIGUE) {
									getOwner().message("You are too tired to smelt cannon ball");
									interrupt();
									return;
								}
							}
							if (getOwner().getCarriedItems().getInventory().countId(ItemId.STEEL_BAR.id()) < 1) {
								getOwner().message("You have no steel bars left");
								interrupt();
								return;
							}
						}
					});
				} else { // No mould
					player.message("you heat the steel bar");
				}
			} else {
				handleRegularSmelting(item, player, obj);
			}
		} else if (obj.getID() == LAVA_FURNACE) {
			int stage = player.getCache().hasKey("miniquest_dwarf_youth_rescue") ? player.getCache().getInt("miniquest_dwarf_youth_rescue") : -1;
			if (stage != 2) {
				player.message("You don't have permission to use this");
				return;
			}
			int amount = 0;
			if (item.getCatalogId() == ItemId.DRAGON_SWORD.id())
				amount = 1;
			else if (item.getCatalogId() == ItemId.DRAGON_AXE.id())
				amount = 2;
			else {
				player.message("Nothing interesting happens");
				return;
			}
			if (getCurrentLevel(player, Skills.SMITHING) < 90) {
				player.message("90 smithing is required to use this forge");
				return;
			}
			if (player.getCarriedItems().remove(new Item(item.getCatalogId())) > -1) {
				player.message("You smelt the " + item.getDef(player.getWorld()).getName() + "...");
				delay(player.getWorld().getServer().getConfig().GAME_TICK * 5);
				player.message("And retrieve " + amount + " dragon bar" + (amount > 1? "s":""));
				give(player, ItemId.DRAGON_BAR.id(), amount);
			}
		}
	}

	private void handleRegularSmelting(final Item item, Player player, final GameObject obj) {
		if (!inArray(item.getCatalogId(), Smelt.ADAMANTITE_ORE.getID(), Smelt.COAL.getID(), Smelt.COPPER_ORE.getID(), Smelt.IRON_ORE.getID(), Smelt.GOLD.getID(), Smelt.MITHRIL_ORE.getID(), Smelt.RUNITE_ORE.getID(), Smelt.SILVER.getID(), Smelt.TIN_ORE.getID(), ItemId.GOLD_FAMILYCREST.id())) {
			player.message("Nothing interesting happens");
			return;
		}
		String formattedName = item.getDef(player.getWorld()).getName().toUpperCase().replaceAll(" ", "_");
		Smelt smelt;
		if (item.getCatalogId() == Smelt.IRON_ORE.getID() && getCurrentLevel(player, Skills.SMITHING) >= 30 && player.getCarriedItems().getInventory().countId(Smelt.COAL.getID()) >= 2) {
			String coalChange = player.getWorld().getServer().getEntityHandler().getItemDef(Smelt.COAL.getID()).getName().toUpperCase();
			smelt = Smelt.valueOf(coalChange);
		} else {
			smelt = Smelt.valueOf(formattedName);
		}

		if (!player.getCarriedItems().getInventory().contains(item)) {
			return;
		}

		if (obj.getLocation().equals(Point.location(399, 840))) {
			// furnace in shilo village
			if ((player.getLocation().getY() == 841 && !player.withinRange(obj, 2)) && !player.withinRange90Deg(obj, 2)) {
				return;
			}
		} else {
			// some furnaces the player is 2 spaces away
			if (!player.withinRange(obj, 1) && !player.withinRange90Deg(obj, 2)) {
				return;
			}
		}

		thinkbubble(player, item);
		if (player.getWorld().getServer().getConfig().WANT_FATIGUE) {
			if (player.getWorld().getServer().getConfig().STOP_SKILLING_FATIGUED >= 2
				&& player.getFatigue() >= player.MAX_FATIGUE) {
				player.message("You are too tired to smelt this ore");
				return;
			}
		}
		if (getCurrentLevel(player, Skills.SMITHING) < smelt.getRequiredLevel()) {
			player.playerServerMessage(MessageType.QUEST, "You need to be at least level-" + smelt.getRequiredLevel() + " smithing to " + (smelt.getSmeltBarId() == ItemId.SILVER_BAR.id() || smelt.getSmeltBarId() == ItemId.GOLD_BAR.id() || smelt.getSmeltBarId() == ItemId.GOLD_BAR_FAMILYCREST.id() ? "work " : "smelt ") + player.getWorld().getServer().getEntityHandler().getItemDef(smelt.getSmeltBarId()).getName().toLowerCase().replaceAll("bar", ""));
			if (smelt.getSmeltBarId() == ItemId.IRON_BAR.id())
				player.playerServerMessage(MessageType.QUEST, "Practice your smithing using tin and copper to make bronze");
			return;
		}
		if (player.getCarriedItems().getInventory().countId(smelt.getReqOreId()) < smelt.getReqOreAmount() || (player.getCarriedItems().getInventory().countId(smelt.getID()) < smelt.getOreAmount() && smelt.getReqOreAmount() != -1)) {
			if (smelt.getID() == Smelt.TIN_ORE.getID() || item.getCatalogId() == Smelt.COPPER_ORE.getID()) {
				player.playerServerMessage(MessageType.QUEST, "You also need some " + (item.getCatalogId() == Smelt.TIN_ORE.getID() ? "copper" : "tin") + " to make bronze");
				return;
			}
			if (smelt.getID() == Smelt.COAL.getID() && (player.getCarriedItems().getInventory().countId(Smelt.IRON_ORE.getID()) < 1 || player.getCarriedItems().getInventory().countId(Smelt.COAL.getID()) <= 1)) {
				player.playerServerMessage(MessageType.QUEST, "You need 1 iron-ore and 2 coal to make steel");
				return;
			} else {
				player.playerServerMessage(MessageType.QUEST, "You need " + smelt.getReqOreAmount() + " heaps of " + player.getWorld().getServer().getEntityHandler().getItemDef(smelt.getReqOreId()).getName().toLowerCase()
					+ " to smelt "
					+ item.getDef(player.getWorld()).getName().toLowerCase().replaceAll("ore", ""));
				return;
			}
		}

		player.playerServerMessage(MessageType.QUEST, smeltString(player.getWorld(), smelt, item));
		player.setBatchEvent(new BatchEvent(player.getWorld(), player, player.getWorld().getServer().getConfig().GAME_TICK * 3, "Smelt", Formulae.getRepeatTimes(player, Skills.SMITHING), false) {
			@Override
			public void action() {
				if (getWorld().getServer().getConfig().WANT_FATIGUE) {
					if (getWorld().getServer().getConfig().STOP_SKILLING_FATIGUED >= 2
						&& getOwner().getFatigue() >= getOwner().MAX_FATIGUE) {
						getOwner().message("You are too tired to smelt this ore");
						interrupt();
						return;
					}
				}
				if (getCurrentLevel(getOwner(), Skills.SMITHING) < smelt.getRequiredLevel()) {
					getOwner().playerServerMessage(MessageType.QUEST, "You need to be at least level-" + smelt.getRequiredLevel() + " smithing to " + (smelt.getSmeltBarId() == ItemId.SILVER_BAR.id() || smelt.getSmeltBarId() == ItemId.GOLD_BAR.id() || smelt.getSmeltBarId() == ItemId.GOLD_BAR_FAMILYCREST.id() ? "work " : "smelt ") + getWorld().getServer().getEntityHandler().getItemDef(smelt.getSmeltBarId()).getName().toLowerCase().replaceAll("bar", ""));
					if (smelt.getSmeltBarId() == ItemId.IRON_BAR.id())
						getOwner().playerServerMessage(MessageType.QUEST, "Practice your smithing using tin and copper to make bronze");
					interrupt();
					return;
				}
				if (getOwner().getCarriedItems().getInventory().countId(smelt.getReqOreId()) < smelt.getReqOreAmount() || (getOwner().getCarriedItems().getInventory().countId(smelt.getID()) < smelt.getOreAmount() && smelt.getReqOreAmount() != -1)) {
					if (smelt.getID() == Smelt.COAL.getID() && (getOwner().getCarriedItems().getInventory().countId(Smelt.IRON_ORE.getID()) < 1 || getOwner().getCarriedItems().getInventory().countId(Smelt.COAL.getID()) <= 1)) {
						getOwner().playerServerMessage(MessageType.QUEST, "You need 1 iron-ore and 2 coal to make steel");
						interrupt();
						return;
					}
					if (smelt.getID() == Smelt.TIN_ORE.getID() || item.getCatalogId() == Smelt.COPPER_ORE.getID()) {
						getOwner().playerServerMessage(MessageType.QUEST, "You also need some " + (item.getCatalogId() == Smelt.TIN_ORE.getID() ? "copper" : "tin") + " to make bronze");
						interrupt();
						return;
					} else {
						getOwner().playerServerMessage(MessageType.QUEST, "You need " + smelt.getReqOreAmount() + " heaps of " + getWorld().getServer().getEntityHandler().getItemDef(smelt.getReqOreId()).getName().toLowerCase()
							+ " to smelt "
							+ item.getDef(getWorld()).getName().toLowerCase().replaceAll("ore", ""));
						interrupt();
						return;
					}
				}
				thinkbubble(getOwner(), item);
				if (getOwner().getCarriedItems().getInventory().countId(item.getCatalogId()) > 0) {
					if (item.getCatalogId() == ItemId.GOLD_FAMILYCREST.id())
						getOwner().getCarriedItems().remove(new Item(ItemId.GOLD_FAMILYCREST.id()));
					else
						getOwner().getCarriedItems().remove(new Item(smelt.getID(), smelt.getOreAmount()));

					if (smelt.getReqOreAmount() > 0)
						getOwner().getCarriedItems().remove(new Item(smelt.getReqOreId(), smelt.getReqOreAmount()));

					if (smelt.getID() == Smelt.IRON_ORE.getID() && DataConversions.random(0, 1) == 1) {
						if (getOwner().getCarriedItems().getEquipment().hasEquipped(ItemId.RING_OF_FORGING.id())) {
							getOwner().message("Your ring of forging shines brightly");
							give(getOwner(), smelt.getSmeltBarId(), 1);
							if (getOwner().getCache().hasKey("ringofforging")) {
								int ringCheck = getOwner().getCache().getInt("ringofforging");
								if (ringCheck + 1 == getWorld().getServer().getConfig().RING_OF_FORGING_USES) {
									getOwner().getCache().remove("ringofforging");
									getOwner().getCarriedItems().getInventory().shatter(ItemId.RING_OF_FORGING.id());
								} else {
									getOwner().getCache().set("ringofforging", ringCheck + 1);
								}
							} else {
								getOwner().getCache().put("ringofforging", 1);
								getOwner().message("You start a new ring of forging");
							}
						} else {
							getOwner().message("The ore is too impure and you fail to refine it");
						}
					} else {
						if (item.getCatalogId() == ItemId.GOLD_FAMILYCREST.id())
							give(getOwner(), ItemId.GOLD_BAR_FAMILYCREST.id(), 1);
						else
							give(getOwner(), smelt.getSmeltBarId(), 1);

						getOwner().playerServerMessage(MessageType.QUEST, "You retrieve a bar of " + new Item(smelt.getSmeltBarId()).getDef(getWorld()).getName().toLowerCase().replaceAll("bar", ""));

						/** Gauntlets of Goldsmithing provide an additional 23 experience when smelting gold ores **/
						if (getOwner().getCarriedItems().getEquipment().hasEquipped(ItemId.GAUNTLETS_OF_GOLDSMITHING.id()) && new Item(smelt.getSmeltBarId()).getCatalogId() == ItemId.GOLD_BAR.id()) {
							getOwner().incExp(Skills.SMITHING, smelt.getXp() + 45, true);
						} else {
							getOwner().incExp(Skills.SMITHING, smelt.getXp(), true);
						}
					}
				} else {
					interrupt();
				}
			}
		});
	}

	private String smeltString(World world, Smelt smelt, Item item) {
		String message = null;
		if (smelt.getSmeltBarId() == ItemId.BRONZE_BAR.id()) {
			message = "You smelt the copper and tin together in the furnace";
		} else if (smelt.getSmeltBarId() == ItemId.MITHRIL_BAR.id() || smelt.getSmeltBarId() == ItemId.ADAMANTITE_BAR.id()|| smelt.getSmeltBarId() == ItemId.RUNITE_BAR.id()) {
			message = "You place the " + item.getDef(world).getName().toLowerCase().replaceAll(" ore", "") + " and " + smelt.getReqOreAmount() + " heaps of " + world.getServer().getEntityHandler().getItemDef(smelt.getReqOreId()).getName().toLowerCase() + " into the furnace";
		} else if (smelt.getSmeltBarId() == ItemId.STEEL_BAR.id()) {
			message = "You place the iron and 2 heaps of coal into the furnace";
		} else if (smelt.getSmeltBarId() == ItemId.IRON_BAR.id()) {
			message = "You smelt the " + item.getDef(world).getName().toLowerCase().replaceAll(" ore", "") + " in the furnace";
		} else if (smelt.getSmeltBarId() == ItemId.SILVER_BAR.id() || smelt.getSmeltBarId() == ItemId.GOLD_BAR.id() || smelt.getSmeltBarId() == ItemId.GOLD_BAR_FAMILYCREST.id()) {
			message = "You place a lump of " + item.getDef(world).getName().toLowerCase().replaceAll(" ore", "") + " in the furnace";
		}
		return message;
	}

	@Override
	public boolean blockUseLoc(GameObject obj, Item item, Player player) {
		return (obj.getID() == FURNACE && !DataConversions.inArray(new int[]{ItemId.GOLD_BAR.id(), ItemId.SILVER_BAR.id(), ItemId.SODA_ASH.id(), ItemId.SAND.id(), ItemId.GOLD_BAR_FAMILYCREST.id()}, item.getCatalogId()))
			|| obj.getID() == LAVA_FURNACE;
	}

	enum Smelt {
		COPPER_ORE(ItemId.COPPER_ORE.id(), 25, 1, 1, 169, 202, 1),
		TIN_ORE(ItemId.TIN_ORE.id(), 25, 1, 1, 169, 150, 1),
		IRON_ORE(ItemId.IRON_ORE.id(), 50, 15, 1, 170, -1, -1),
		SILVER(ItemId.SILVER.id(), 54, 20, 1, 384, -1, -1),
		GOLD(ItemId.GOLD.id(), 90, 40, 1, 172, -1, -1),
		MITHRIL_ORE(ItemId.MITHRIL_ORE.id(), 120, 50, 1, 173, 155, 4),
		ADAMANTITE_ORE(ItemId.ADAMANTITE_ORE.id(), 150, 70, 1, 174, 155, 6),
		COAL(ItemId.COAL.id(), 70, 30, 2, 171, 151, 1),
		RUNITE_ORE(ItemId.RUNITE_ORE.id(), 200, 85, 1, 408, 155, 8);

		private final int id;
		private final int xp;
		private final int requiredLevel;
		private final int oreAmount;
		private final int smeltBarId;
		private final int requestedOreId;
		private final int requestedOreAmount;

		Smelt(int itemId, int exp, int req, int oreAmount, int barId, int reqOreId, int reqOreAmount) {
			this.id = itemId;
			this.xp = exp;
			this.requiredLevel = req;
			this.oreAmount = oreAmount;
			this.smeltBarId = barId;
			this.requestedOreId = reqOreId;
			this.requestedOreAmount = reqOreAmount;
		}

		public int getID() {
			return id;
		}

		public int getXp() {
			return xp;
		}

		public int getRequiredLevel() {
			return requiredLevel;
		}

		public int getOreAmount() {
			return oreAmount;
		}

		public int getSmeltBarId() {
			return smeltBarId;
		}

		public int getReqOreId() {
			return requestedOreId;
		}

		public int getReqOreAmount() {
			return requestedOreAmount;
		}
	}
}