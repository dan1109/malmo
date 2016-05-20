package com.microsoft.Malmo.Utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 *  Utility functions for dealing with Minecraft block types, item types, etc.
 */
public class MinecraftTypeHelper
{
    /**
     * Attempts to parse the block type string.
     * @param s The string to parse.
     * @return The block type, or null if the string is not recognised.
     */
    public static IBlockState ParseBlockType( String s )
    {
        if( s == null )
            return null; 
        Block block = (Block)Block.blockRegistry.getObject(new ResourceLocation( s ));
        if( block instanceof BlockAir && !s.equals("air") ) // Minecraft returns BlockAir when it doesn't recognise the string
            return null; // unrecognised string
        return block.getDefaultState();
    }
    
    /**
     * Attempts to parse the item type string.
     * @param s The string to parse.
     * @return The item type, or null if the string is not recognised.
     */
    public static Item ParseItemType( String s )
    {
        if (s == null)
            return null;
        Item item = (Item)Item.itemRegistry.getObject(new ResourceLocation(s)); // Minecraft returns null when it doesn't recognise the string
        return item;
    }
}
