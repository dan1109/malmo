package com.microsoft.Malmo.MissionHandlers;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.microsoft.Malmo.MalmoMod;
import com.microsoft.Malmo.MalmoMod.MalmoMessageType;
import com.microsoft.Malmo.Schemas.MissionInit;
import com.microsoft.Malmo.Schemas.ServerQuitFromTimeUp;

/** IWantToQuit object that returns true when a certain amount of time has elapsed.<br>
 * This object also draws a cheeky countdown on the Minecraft Chat HUD.
 */

public class ServerQuitFromTimeUpImplementation extends QuitFromTimeUpBase
{
	private String quitCode = "";
	
	@Override
	public boolean parseParameters(Object params)
	{
		if (params == null || !(params instanceof ServerQuitFromTimeUp))
			return false;
		
		ServerQuitFromTimeUp qtuparams = (ServerQuitFromTimeUp)params;
		setTimeLimitMs(qtuparams.getTimeLimitMs().intValue());
		this.quitCode = qtuparams.getDescription();
		return true;
	}

	@Override
	protected long getWorldTime()
	{
	   	World world = null;
	   	MinecraftServer server = MinecraftServer.getServer();
    	if (server.worldServers != null && server.worldServers.length != 0)
    		world = server.getEntityWorld();
		return (world != null) ? world.getTotalWorldTime() : 0;
	}
	
	@Override
	protected void drawCountDown(int secondsRemaining)
	{
		Map<String, String> data = new HashMap<String, String>();
		
        String text = EnumChatFormatting.BOLD + "" + secondsRemaining + "...";
        if (secondsRemaining <= 5)
            text = EnumChatFormatting.RED + text;

		data.put("chat", text);
		MalmoMod.safeSendToAll(MalmoMessageType.SERVER_TEXT, data);
	}

	@Override
    public void prepare(MissionInit missionInit) {}

	@Override
    public void cleanup() {}
	
	@Override
	public String getOutcome()
	{
		return this.quitCode;
	}
}