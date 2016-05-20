package com.microsoft.Malmo.MissionHandlers;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;

import com.microsoft.Malmo.MissionHandlerInterfaces.IRewardProducer;
import com.microsoft.Malmo.Schemas.Behaviour;
import com.microsoft.Malmo.Schemas.BlockSpecWithRewardAndBehaviour;
import com.microsoft.Malmo.Schemas.BlockType;
import com.microsoft.Malmo.Schemas.BlockVariant;
import com.microsoft.Malmo.Schemas.Colour;
import com.microsoft.Malmo.Schemas.MissionInit;
import com.microsoft.Malmo.Schemas.RewardForTouchingBlockType;
import com.microsoft.Malmo.Utils.PositionHelper;

public class RewardForTouchingBlockTypeImplementation extends HandlerBase implements IRewardProducer
{
	private class BlockMatcher
	{
		boolean hasFired = false;
		BlockSpecWithRewardAndBehaviour spec;
		ArrayList<String> allowedBlockNames;
		ArrayList<BlockPos> firedBlocks = new ArrayList<BlockPos>();
		long lastFired;
		
		BlockMatcher(BlockSpecWithRewardAndBehaviour spec)
		{
			this.spec = spec;

			// Get the allowed blocks:
			// (Convert from the enum name to the unlocalised name.)
			this.allowedBlockNames = new ArrayList<String>();
			List<BlockType> allowedTypes = spec.getType();
			if (allowedTypes != null)
			{
				for (BlockType bt : allowedTypes)
				{
					Block b = Block.getBlockFromName(bt.value());
					this.allowedBlockNames.add(b.getUnlocalizedName());
				}
			}
		}
		
		boolean applies(BlockPos bp)
		{
			switch (this.spec.getBehaviour())
			{
			case ONCE_ONLY:
				return !this.hasFired;
				
			case ONCE_PER_BLOCK:
				return !this.firedBlocks.contains(bp);
				
			case ONCE_PER_TIME_SPAN:
				return this.spec.getCooldownInMs().floatValue() < System.currentTimeMillis() - this.lastFired;
				
			case CONSTANT:
				return true;
			}
			return true;
		}
		
		boolean matches(BlockPos bp, IBlockState bs)
		{
			boolean match = false;

			// See whether the blockstate matches our specification:
			for (String allowedbs : this.allowedBlockNames)
			{
				if (allowedbs.equals(bs.getBlock().getUnlocalizedName()))
					match = true;
			}

			if (match && this.spec.getColour() != null && !this.spec.getColour().isEmpty())
			{
				// This type of block is a match, but does the colour match?
				for (IProperty prop : (java.util.Set<IProperty>)bs.getProperties().keySet())
				{
					if (prop.getName().equals("color") && prop.getValueClass() == net.minecraft.item.EnumDyeColor.class)
					{
						// The block in question has a colour, so check it is a specified one:
						match = false;	// Assume no match.
						net.minecraft.item.EnumDyeColor current = (net.minecraft.item.EnumDyeColor)bs.getValue(prop);
						for (Colour col : this.spec.getColour())
						{
							if (current.getName().equalsIgnoreCase(col.name()))
								match = true;
						}
					}
				}
			}
			
			if (match && this.spec.getVariant() != null && !this.spec.getVariant().isEmpty())
			{
				// Matches type and colour, but does the variant match?
		        for (IProperty prop : (java.util.Set<IProperty>)bs.getProperties().keySet())
		        {
		            if (prop.getName().equals("variant") && prop.getValueClass().isEnum())
		            {
		            	Object current = bs.getValue(prop);
		            	if (current != null)
		            	{
			            	match = false;	// Assume no match.
			            	for (BlockVariant var : this.spec.getVariant())
			            	{
			            		if (var.name().equalsIgnoreCase(current.toString()))
			            			match = true;
			            	}
		            	}
		            }
		        }
			}
			
			if (match)
			{
				// We're firing.
				this.hasFired = true;
				this.lastFired = System.currentTimeMillis();
				if (this.spec.getBehaviour() == Behaviour.ONCE_PER_BLOCK)
					this.firedBlocks.add(bp);
			}

			return match;
		}
		
		float reward()
		{
			return this.spec.getReward().floatValue();
		}
	}
	
	ArrayList<BlockMatcher> matchers = new ArrayList<BlockMatcher>();
	
	@Override
	public boolean parseParameters(Object params)
	{
		if (params == null || !(params instanceof RewardForTouchingBlockType))
			return false;
		
		RewardForTouchingBlockType rtbtparams = (RewardForTouchingBlockType)params;
		for (BlockSpecWithRewardAndBehaviour spec : rtbtparams.getBlock())
			this.matchers.add(new BlockMatcher(spec));
			
		return true;
	}

	@Override
	public float getReward(MissionInit missionInit)
	{
		// Determine what blocks we are touching.
		// This code is largely cribbed from Entity, where it is used to fire the Block.onEntityCollidedWithBlock methods.
		EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;

        float reward = 0;
       
        List<BlockPos> touchingBlocks = PositionHelper.getTouchingBlocks(player);
        for (BlockPos pos : touchingBlocks)
        {
            IBlockState iblockstate = player.worldObj.getBlockState(pos);
            for (BlockMatcher bm : this.matchers)
            {
            	if (bm.applies(pos) && bm.matches(pos, iblockstate))
            		reward += bm.reward();
            }
        }
        return reward;
    }

	@Override
	public void prepare(MissionInit missionInit) {}

	@Override
    public void cleanup() {}
}
