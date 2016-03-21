package mca.core.forge;

import java.util.ArrayList;
import java.util.List;

import mca.core.Constants;
import mca.core.MCA;
import mca.core.minecraft.ModAchievements;
import mca.core.minecraft.ModItems;
import mca.data.PlayerData;
import mca.entity.EntityHuman;
import mca.enums.EnumProfession;
import mca.enums.EnumProfessionGroup;
import mca.items.ItemGemCutter;
import mca.packets.PacketSyncConfig;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemSmeltedEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import radixcore.constant.Time;
import radixcore.math.Point3D;
import radixcore.packets.PacketDataContainer;
import radixcore.util.BlockHelper;
import radixcore.util.RadixLogic;
import radixcore.util.RadixMath;
import radixcore.util.SchematicHandler;

public class EventHooksFML 
{
	public static boolean playPortalAnimation;
	private int clientTickCounter;
	private int serverTickCounter;

	@SubscribeEvent
	public void onConfigChanges(ConfigChangedEvent.OnConfigChangedEvent eventArgs)
	{
		if (eventArgs.modID.equals(MCA.ID))
		{
			MCA.getConfig().getInstance().save();
			MCA.getConfig().syncConfiguration();
		}
	}

	@SubscribeEvent
	public void playerLoggedInEventHandler(PlayerLoggedInEvent event)
	{
		EntityPlayer player = event.player;
		PlayerData data = null;

		if (!MCA.playerDataMap.containsKey(player.getUniqueID().toString()))
		{
			data = new PlayerData(player);

			if (data.dataExists())
			{
				data = data.readDataFromFile(event.player, PlayerData.class, null);
			}

			else
			{
				data.initializeNewData(event.player);
			}

			MCA.playerDataMap.put(event.player.getUniqueID().toString(), data);
		}

		else
		{
			data = MCA.getPlayerData(player);

			if (data != null) //Very rare issue, ignore for now but look into later TODO
			{
				data = data.readDataFromFile(event.player, PlayerData.class, null);  //Read from the file again to assign owner.
				MCA.playerDataMap.put(event.player.getUniqueID().toString(), data);  //Put updated data back into the map.
			}
		}

		if (data != null)
		{
			MCA.getPacketHandler().sendPacketToPlayer(new PacketDataContainer(MCA.ID, data), (EntityPlayerMP)event.player);
			MCA.getPacketHandler().sendPacketToPlayer(new PacketSyncConfig(MCA.getConfig()), (EntityPlayerMP)event.player);

			if (!data.getHasChosenDestiny() && !player.inventory.hasItemStack(new ItemStack(ModItems.crystalBall)) && MCA.getConfig().giveCrystalBall)
			{
				player.inventory.addItemStackToInventory(new ItemStack(ModItems.crystalBall));
			}
		}
		
		else
		{
			MCA.getLog().warn("Unable to initialize player data for " + event.player.getName() + ". Did you update from a previous version without clearing player data?");
			MCA.getLog().warn("If not, please report this issue at http://github.com/minecraft-comes-alive/issues.");
		}
	}

	@SubscribeEvent
	public void playerLoggedOutEventHandler(PlayerLoggedOutEvent event)
	{
		PlayerData data = MCA.getPlayerData(event.player);

		if (data != null)
		{
			data.saveDataToFile();
		}
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void clientTickEventHandler(ClientTickEvent event)
	{
		MCA.getPacketHandler().processPackets(Side.CLIENT);

		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
		net.minecraft.client.gui.GuiScreen currentScreen = mc.currentScreen;

		if (currentScreen instanceof net.minecraft.client.gui.GuiMainMenu && MCA.playerDataContainer != null)
		{
			playPortalAnimation = false;
			MCA.destinyCenterPoint = null;
			MCA.destinySpawnFlag = false;
			MCA.playerDataContainer = null;
			MCA.resetConfig();
		}

		//Check for setting/processing the flag for loading language again.
		if (currentScreen instanceof net.minecraft.client.gui.GuiLanguage)
		{
			MCA.reloadLanguage = true;
		}

		else if (MCA.reloadLanguage)
		{
			MCA.reloadLanguage = false;
			MCA.getLanguageManager().loadLanguage(MCA.getLanguageManager().getGameLanguageID());
		}

		if (playPortalAnimation)
		{
			EntityPlayerSP player = (EntityPlayerSP)mc.thePlayer;

			if (player == null)
			{
				return; //Crash when kicked from a server while using the ball. Client-side, so just throw it out.
			}

			player.prevTimeInPortal = player.timeInPortal;
			player.timeInPortal -= 0.0125F;

			if (player.timeInPortal <= 0.0F)
			{
				playPortalAnimation = false;
			}
		}

		if (clientTickCounter <= 0)
		{
			clientTickCounter = Time.SECOND / 2;

			if (MCA.destinySpawnFlag)
			{
				SchematicHandler.spawnStructureRelativeToPoint("/assets/mca/schematic/destiny-test.schematic", MCA.destinyCenterPoint, mc.theWorld);
			}
		}

		else
		{
			clientTickCounter--;
		}
	}

	@SubscribeEvent
	public void serverTickEventHandler(ServerTickEvent event)
	{
		MCA.getPacketHandler().processPackets(Side.SERVER);

		// This block prevents the long-standing issue of crashing while using a world that previously contained villagers.
		// It will check every second for a villager that has not been converted, and see if it should be. These villagers
		// are identified by having the value of 3577 for watched object number 28.
		if (serverTickCounter % 40 == 0)
		{
			for (World world : FMLCommonHandler.instance().getMinecraftServerInstance().worldServers)
			{
				for (int i = 0; i < world.loadedEntityList.size(); i++)
				{
					Object obj = world.loadedEntityList.get(i);

					if (obj instanceof EntityVillager)
					{
						EntityVillager villager = (EntityVillager)obj;

						try
						{
							if (villager.getDataManager().get(Constants.OVERWRITE_KEY) == 3577)
							{
								doOverwriteVillager(villager);
							}
						}

						catch (Exception e)
						{
							continue;
						}
					}
				}
			}
		}

		if (serverTickCounter <= 0 && MCA.getConfig().guardSpawnRate > 0)
		{
			//Build a list of all humans on the server.
			List<EntityHuman> humans = new ArrayList<EntityHuman>();

			for (World world : FMLCommonHandler.instance().getMinecraftServerInstance().worldServers)
			{
				for (Object obj : world.loadedEntityList)
				{
					if (obj instanceof EntityHuman)
					{
						humans.add((EntityHuman)obj);
					}
				}
			}

			if (!humans.isEmpty())
			{
				//Pick three at random.
				for (int i = 0; i < 3; i++)
				{
					EntityHuman human = humans.get(RadixMath.getNumberInRange(0, humans.size() - 1));

					int neededNumberOfGuards = RadixLogic.getAllEntitiesOfTypeWithinDistance(EntityHuman.class, human, 50).size() / MCA.getConfig().guardSpawnRate;
					int numberOfGuards = 0;

					for (Entity entity : RadixLogic.getAllEntitiesOfTypeWithinDistance(EntityHuman.class, human, 50))
					{
						if (entity instanceof EntityHuman)
						{
							EntityHuman otherHuman = (EntityHuman)entity;

							if (otherHuman.getProfessionGroup() == EnumProfessionGroup.Guard)
							{
								numberOfGuards++;
							}
						}
					}

					if (numberOfGuards < neededNumberOfGuards)
					{
						final EntityHuman guard = new EntityHuman(human.worldObj, RadixLogic.getBooleanWithProbability(50), EnumProfession.Guard.getId(), false);
						final Vec3d pos = RandomPositionGenerator.findRandomTarget(human, 10, 1);

						if (pos != null) //Ensure a random position was actually found.
						{
							final Point3D posAsPoint = new Point3D(pos.xCoord, pos.yCoord, pos.zCoord);

							//Check that we can see the sky, no guards in caves or stuck in blocks.
							if (BlockHelper.canBlockSeeTheSky(human.worldObj, posAsPoint.iPosX, (int)human.posY, posAsPoint.iPosZ))
							{
								guard.setPosition(pos.xCoord, (int)human.posY, pos.zCoord);
								human.worldObj.spawnEntityInWorld(guard);
							}
						}
					}
				}
			}

			serverTickCounter = Time.MINUTE;
		}

		serverTickCounter--;
	}

	@SubscribeEvent
	public void itemCraftedEventHandler(ItemCraftedEvent event)
	{
		Item craftedItem = event.crafting.getItem();
		EntityPlayer player = event.player;

		if (craftedItem == ModItems.diamondHeart || craftedItem == ModItems.diamondOval || craftedItem == ModItems.diamondSquare
				|| craftedItem == ModItems.diamondStar || craftedItem == ModItems.diamondTiny || craftedItem == ModItems.diamondTriangle)
		{
			player.addStat(ModAchievements.craftShapedDiamond);
		}

		else if (craftedItem == ModItems.engagementRingHeart || craftedItem == ModItems.engagementRingOval || craftedItem == ModItems.engagementRingSquare
				|| craftedItem == ModItems.engagementRingStar || craftedItem == ModItems.engagementRingTiny || craftedItem == ModItems.engagementRingTriangle
				|| craftedItem == ModItems.engagementRingHeartRG || craftedItem == ModItems.engagementRingOvalRG || craftedItem == ModItems.engagementRingSquareRG
				|| craftedItem == ModItems.engagementRingStarRG || craftedItem == ModItems.engagementRingTinyRG || craftedItem == ModItems.engagementRingTriangleRG)
		{
			player.addStat(ModAchievements.craftShapedRing);
		}

		//Return damageable items to the inventory.
		for (int i = 0; i < event.craftMatrix.getSizeInventory(); i++)
		{
			ItemStack stack = event.craftMatrix.getStackInSlot(i);

			if (stack != null && (stack.getItem() instanceof ItemGemCutter || stack.getItem() == ModItems.needleAndString))
			{
				stack.attemptDamageItem(1, event.player.getRNG());

				if (stack.getItemDamage() < stack.getMaxDamage())
				{
					event.player.inventory.addItemStackToInventory(stack);
				}
				player.addStat(ModAchievements.craftShapedDiamond);
			}

			break;
		}
	}
	@SubscribeEvent
	public void itemSmeltedEventHandler(ItemSmeltedEvent event)
	{
		Item smeltedItem = event.smelting.getItem();
		EntityPlayer player = event.player;
	}

	private void doOverwriteVillager(EntityVillager entity) 
	{
		entity.setDead();
		MCA.naturallySpawnVillagers(new Point3D(entity.posX, entity.posY, entity.posZ), entity.worldObj, entity.getProfession());
	}
}
