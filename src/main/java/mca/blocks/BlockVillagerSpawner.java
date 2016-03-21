package mca.blocks;

import java.util.List;
import java.util.Random;

import mca.core.MCA;
import mca.entity.EntityHuman;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import radixcore.constant.Time;
import radixcore.util.BlockHelper;
import radixcore.util.RadixLogic;

public class BlockVillagerSpawner extends Block
{
	public BlockVillagerSpawner()
	{
		super(Material.iron);
		
		setCreativeTab(MCA.getCreativeTabMain());
		setUnlocalizedName("VillagerSpawner");
		setTickRandomly(true);
		setHardness(1.0F);

		GameRegistry.registerBlock(this, "VillagerSpawner");
	}

	public int tickRate()
	{
		return Time.MINUTE * 3;
	}

	@Override
	public void updateTick(World world, BlockPos pos, IBlockState state, Random random) 
	{
		super.updateTick(world, pos, state, random);

		List<Entity> nearbyEntities = RadixLogic.getAllEntitiesWithinDistanceOfCoordinates(world, pos.getX(), pos.getY(), pos.getZ(), 32);
		int nearbyHumans = 0;

		for (Entity entity : nearbyEntities)
		{
			if (entity instanceof EntityHuman)
			{
				nearbyHumans++;
			}
		}

		if (nearbyHumans < MCA.getConfig().villagerSpawnerCap)
		{
			int spawnY = pos.getY();
			boolean continueSpawning = false;

			while (spawnY < 256)
			{
				Block block = BlockHelper.getBlock(world, pos.getX(), spawnY, pos.getZ());
				Block blockAbove = BlockHelper.getBlock(world, pos.getX(), spawnY + 1, pos.getZ());

				if (block == Blocks.air && blockAbove == Blocks.air)
				{
					continueSpawning = true;
					break;
				}

				else
				{
					spawnY++;
				}
			}

			if (continueSpawning)
			{
				final EntityHuman human = new EntityHuman(world, world.rand.nextBoolean());
				human.setPositionAndRotation((double) pos.getX() + 0.5F, (double) spawnY, (double) pos.getZ() + 0.5F, (float)random.nextInt(360) + 1, 0.0F);
				world.spawnEntityInWorld(human);
			}
		}

		world.scheduleUpdate(pos, this, tickRate());
	}
}
