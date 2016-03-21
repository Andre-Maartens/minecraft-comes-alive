package mca.util;

import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketParticles;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

public class Utilities 
{
	private Utilities()
	{
		
	}
	
	public static boolean isPointClear(World world, int posX, int posY, int posZ)
	{
		BlockPos block = new BlockPos(posX, posY, posZ);
		
		return !world.getBlockState(block).getMaterial().blocksMovement();
	}
	
	public static double getNumberInRange(Random rand, float standardDeviation, float mean)
	{
		return (rand.nextGaussian() * standardDeviation) + mean;
	}
	
	public static void spawnParticlesAroundEntityS(EnumParticleTypes type, Entity entityOrigin, int rate)
	{
		final Random rand = entityOrigin.worldObj.rand;

		for (int i = 0; i < rate; i++)
		{
			final float parX = (float) (entityOrigin.posX + rand.nextFloat() * entityOrigin.width * 2.0F - entityOrigin.width);
			final float parY = (float) (entityOrigin.posY + 0.5D + rand.nextFloat() * entityOrigin.height);
			final float parZ = (float) (entityOrigin.posZ + rand.nextFloat() * entityOrigin.width * 2.0F - entityOrigin.width);

			final float velX = (float) (rand.nextGaussian() * 0.02D);
			final float velY = (float) (rand.nextGaussian() * 0.02D);
			final float velZ = (float) (rand.nextGaussian() * 0.02D);

			SPacketParticles packet = new SPacketParticles(type, true, parX, parY, parZ, velX, velY, velZ, 0.0F, 0);

			for (int j = 0; j < entityOrigin.worldObj.playerEntities.size(); ++j)
			{
				EntityPlayerMP entityPlayerMP = (EntityPlayerMP)entityOrigin.worldObj.playerEntities.get(j);
				ChunkCoordIntPair chunkCoordinates = new ChunkCoordIntPair(entityPlayerMP.chunkCoordX, entityPlayerMP.chunkCoordZ);
				double deltaX = entityOrigin.posX - chunkCoordinates.chunkXPos;
				double deltaY = entityOrigin.posY - entityPlayerMP.chunkCoordY;
				double deltaZ = entityOrigin.posZ - chunkCoordinates.chunkZPos;
				double distanceSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

				if (distanceSq <= 256.0D)
				{
					entityPlayerMP.playerNetServerHandler.sendPacket(packet);
				}
			}
		}
	}
	
	public static void spawnParticlesAroundEntityC(EnumParticleTypes type, Entity entityOrigin, int rate)
	{
		final Random rand = entityOrigin.worldObj.rand;

		for (int i = 0; i < rate; i++)
		{
			final float parX = (float) (entityOrigin.posX + rand.nextFloat() * entityOrigin.width * 2.0F - entityOrigin.width);
			final float parY = (float) (entityOrigin.posY + 0.5D + rand.nextFloat() * entityOrigin.height);
			final float parZ = (float) (entityOrigin.posZ + rand.nextFloat() * entityOrigin.width * 2.0F - entityOrigin.width);

			final float velX = (float) (rand.nextGaussian() * 0.02D);
			final float velY = (float) (rand.nextGaussian() * 0.02D);
			final float velZ = (float) (rand.nextGaussian() * 0.02D);

			entityOrigin.worldObj.spawnParticle(type, parX, parY, parZ, velX, velY, velZ);
		}
	}
}
